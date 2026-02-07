package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Advisor для точного поиска узлов по запросу.
 * Извлекает из запроса информацию о классе и методе с помощью LLM,
 * затем ищет соответствующие узлы в базе данных.
 */
@Component
class ExactNodeSearchAdvisor(
    @param:Qualifier("fastExtractionChatClient")
    private val chatClient: ChatClient,
    private val nodeRepository: NodeRepository,
    private val applicationRepository: ApplicationRepository,
    private val objectMapper: ObjectMapper,
) : QueryProcessingAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "ExactNodeSearch"

    override fun getOrder(): Int = 5

    override fun process(context: QueryProcessingContext): Boolean {
        val query = context.currentQuery

        // Пропускаем, если уже есть найденные узлы
        if (context.hasMetadata(QueryMetadataKeys.EXACT_NODES)) {
            log.debug("Пропуск ExactNodeSearch, так как узлы уже найдены ранее.")
            return true
        }

        log.info(query)
        try {
            // Извлекаем информацию о классе и методе с помощью LLM
            val extractionResult = extractClassAndMethod(query)

            if (extractionResult == null) {
                log.debug("Не удалось извлечь класс/метод из запроса: {}", query)
                return true
            }

            log.info("Начат точный поиск. Извлечено из запроса: класс='{}', метод='{}'",
                extractionResult.className, extractionResult.methodName)

            val foundNodes = findNodes(extractionResult.className, extractionResult.methodName, context)

            if (foundNodes.isNotEmpty()) {
                context.setMetadata(QueryMetadataKeys.EXACT_NODES, foundNodes)
                context.setMetadata(
                    QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
                    mapOf(
                        "className" to (extractionResult.className ?: ""),
                        "methodName" to (extractionResult.methodName ?: ""),
                    ),
                )

                context.addStep(
                    ProcessingStep(
                        advisorName = getName(),
                        input = query,
                        output = "Найдено узлов: ${foundNodes.size}. Класс: ${extractionResult.className}, Метод: ${extractionResult.methodName}",
                    ),
                )

                log.info("Успешно найдено {} узлов для класса '{}' и метода '{}'", foundNodes.size, extractionResult.className, extractionResult.methodName)
            } else {
                log.info("Точный поиск завершен: узлы не найдены для класса '{}' и метода '{}'", extractionResult.className, extractionResult.methodName)
            }
        } catch (e: ResourceAccessException) {
            val cause = e.cause
            when {
                cause is SocketTimeoutException || cause is TimeoutException -> {
                    log.warn("Таймаут при извлечении класса/метода из запроса через LLM (запрос: '{}'). Продолжаем без точного поиска.", query)
                }
                else -> {
                    log.warn("Ошибка доступа к LLM при извлечении класса/метода: {} (запрос: '{}')", e.message, query)
                }
            }
        } catch (e: Exception) {
            log.error("Ошибка при точном поиске узлов: {} (запрос: '{}')", e.message ?: e.javaClass.simpleName, query, e)
        }

        return true
    }

    /**
     * Извлекает информацию о классе и методе из запроса с помощью LLM
     * При ошибках LLM пытается использовать простой парсинг как fallback
     */
    private fun extractClassAndMethod(query: String): ExtractionResult? {
        try {
            val prompt = """
                Проанализируй следующий запрос и извлеки информацию о классе и методе, если они упомянуты.
                
                Примеры запросов:
                - "класс abcd, метод doSume" -> {"className": "abcd", "methodName": "doSume"}
                - "метод calculate в классе UserService" -> {"className": "UserService", "methodName": "calculate"}
                - "класс MyClass" -> {"className": "MyClass", "methodName": null}
                - "метод processData" -> {"className": null, "methodName": "processData"}
                
                ВАЖНО:
                - Сохраняй точные названия классов и методов БЕЗ ИЗМЕНЕНИЙ (с учетом регистра, цифр, специальных символов)
                - Если класс или метод не упомянуты, верни null для соответствующего поля
                - Ответь ТОЛЬКО валидным JSON в формате: {"className": "название_класса_или_null", "methodName": "название_метода_или_null"}
                - Не добавляй никаких комментариев или дополнительного текста, только JSON
                
                Запрос: $query
                
                JSON ответ:
            """.trimIndent()

            val startTime = System.currentTimeMillis()
            val response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                ?.trim()
                ?: return trySimpleParsing(query)
            
            val duration = System.currentTimeMillis() - startTime
            log.info("LLM ответил за {} мс (промпт: {} символов, модель: qwen2.5:0.5b)", duration, prompt.length)
            
            if (duration > 10000) {
                log.warn("Медленный ответ LLM: {} мс для простого запроса извлечения (промпт: {} символов). Рекомендуется проверить производительность Ollama.", duration, prompt.length)
            } else if (duration > 5000) {
                log.debug("Умеренно медленный ответ LLM: {} мс", duration)
            }

            return try {
                val json = cleanJsonResponse(response)
                val map = objectMapper.readValue(json, Map::class.java) as Map<*, *>

                val className = map["className"] as? String
                val methodName = map["methodName"] as? String
                if (className == null && methodName == null) {
                    trySimpleParsing(query)
                } else {
                    ExtractionResult(
                        className = className?.takeIf { it.isNotBlank() },
                        methodName = methodName?.takeIf { it.isNotBlank() },
                    )
                }
            } catch (e: Exception) {
                log.info("Не удалось распарсить ответ LLM, переключаемся на простой парсинг. Ошибка: {}", e.message)
                trySimpleParsing(query)
            }
        } catch (e: ResourceAccessException) {
            log.info("Ошибка доступа к LLM ({}), переключаемся на простой парсинг (Regex).", e.message)
            return trySimpleParsing(query)
        } catch (e: Exception) {
            log.info("Неожиданная ошибка при извлечении через LLM ({}), переключаемся на простой парсинг.", e.message)
            return trySimpleParsing(query)
        }
    }

    /**
     * Простой парсинг запроса без использования LLM (fallback)
     */
    private fun trySimpleParsing(query: String): ExtractionResult? {
        val classPatterns = listOf(
            Regex("класс\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
            Regex("class\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
        )

        val methodPatterns = listOf(
            Regex("метод\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
            Regex("method\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
            Regex("функция\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
            Regex("function\\s+([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE),
        )

        var className: String? = null
        var methodName: String? = null

        for (pattern in classPatterns) {
            val match = pattern.find(query)
            if (match != null) {
                className = match.groupValues[1]
                break
            }
        }

        for (pattern in methodPatterns) {
            val match = pattern.find(query)
            if (match != null) {
                methodName = match.groupValues[1]
                break
            }
        }
        if (className == null && methodName == null) {
            return null
        }
        log.info("Простой парсинг (Regex) извлек: класс='{}', метод='{}'", className, methodName)

        return ExtractionResult(className, methodName)
    }

    /**
     * Очищает JSON ответ от возможных markdown форматирования
     */
    private fun cleanJsonResponse(response: String): String {
        return response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Ищет узлы по имени класса и метода
     */
    private fun findNodes(className: String?, methodName: String?, context: QueryProcessingContext): List<Node> {
        val foundNodes = mutableListOf<Node>()

        val applicationId = context.getMetadata<Long>(QueryMetadataKeys.APPLICATION_ID)
        val applications = if (applicationId != null) {
            applicationRepository.findById(applicationId).map { listOf(it) }.orElse(emptyList())
        } else {
            applicationRepository.findAll()
        }

        if (applications.isEmpty()) {
            log.warn("Не найдено приложений для поиска узлов (Application table empty or ID not found)")
            return foundNodes
        }

        if (className != null && methodName != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                    applicationId = appId,
                    className = className,
                    methodName = methodName,
                    methodKind = NodeKind.METHOD,
                )
                foundNodes.addAll(nodes)
            }
        } else if (className != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndClassName(
                    applicationId = appId,
                    className = className,
                    classKinds = setOf(NodeKind.CLASS, NodeKind.INTERFACE),
                )
                foundNodes.addAll(nodes)
            }
        } else if (methodName != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndMethodName(
                    applicationId = appId,
                    methodName = methodName,
                    methodKind = NodeKind.METHOD,
                )
                foundNodes.addAll(nodes)
            }
        }

        return foundNodes.distinctBy { it.id }
    }

    /**
     * Результат извлечения информации о классе и методе
     */
    private data class ExtractionResult(
        val className: String?,
        val methodName: String?,
    )
}