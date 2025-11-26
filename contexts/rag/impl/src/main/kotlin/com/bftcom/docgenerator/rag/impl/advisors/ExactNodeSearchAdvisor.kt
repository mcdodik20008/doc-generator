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
    @Qualifier("ragChatClient")
    private val chatClient: ChatClient,
    private val nodeRepository: NodeRepository,
    private val applicationRepository: ApplicationRepository,
    private val objectMapper: ObjectMapper,
) : QueryProcessingAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "ExactNodeSearch"

    override fun getOrder(): Int = 5 // Выполняется раньше других advisors

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
                // Оставляем debug, чтобы не спамить в info на каждый общий запрос
                log.debug("Не удалось извлечь класс/метод из запроса: {}", query)
                return true
            }

            // [LOG INFO] Зафиксировали намерение поиска
            log.info("Начат точный поиск. Извлечено из запроса: класс='{}', метод='{}'",
                extractionResult.className, extractionResult.methodName)

            // Ищем узлы
            val foundNodes = findNodes(extractionResult.className, extractionResult.methodName)

            if (foundNodes.isNotEmpty()) {
                context.setMetadata(QueryMetadataKeys.EXACT_NODES, foundNodes)
                // Сохраняем как Map для удобного доступа в других местах
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

                // [LOG INFO] Успешный поиск
                log.info("Успешно найдено {} узлов для класса '{}' и метода '{}'", foundNodes.size, extractionResult.className, extractionResult.methodName)
            } else {
                // [LOG INFO] Поиск не дал результатов (важно знать, что мы пытались, но не нашли)
                log.info("Точный поиск завершен: узлы не найдены для класса '{}' и метода '{}'", extractionResult.className, extractionResult.methodName)
            }
        } catch (e: ResourceAccessException) {
            // Специфичная обработка сетевых ошибок и таймаутов
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
        // Сначала пытаемся использовать LLM
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

            log.info(prompt.length.toString())
            val response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                ?.trim()
                ?: return trySimpleParsing(query)

            return try {
                // Пытаемся распарсить JSON ответ
                val json = cleanJsonResponse(response)
                val map = objectMapper.readValue(json, Map::class.java) as Map<*, *>

                val className = map["className"] as? String
                val methodName = map["methodName"] as? String

                // Если оба поля null, значит ничего не найдено
                if (className == null && methodName == null) {
                    trySimpleParsing(query)
                } else {
                    ExtractionResult(
                        className = className?.takeIf { it.isNotBlank() },
                        methodName = methodName?.takeIf { it.isNotBlank() },
                    )
                }
            } catch (e: Exception) {
                // [LOG INFO] Fallback - проблема с парсингом JSON
                log.info("Не удалось распарсить ответ LLM, переключаемся на простой парсинг. Ошибка: {}", e.message)
                trySimpleParsing(query)
            }
        } catch (e: ResourceAccessException) {
            // [LOG INFO] Fallback - проблема сети
            log.info("Ошибка доступа к LLM ({}), переключаемся на простой парсинг (Regex).", e.message)
            return trySimpleParsing(query)
        } catch (e: Exception) {
            // [LOG INFO] Fallback - общая ошибка
            log.info("Неожиданная ошибка при извлечении через LLM ({}), переключаемся на простой парсинг.", e.message)
            return trySimpleParsing(query)
        }
    }

    /**
     * Простой парсинг запроса без использования LLM (fallback)
     */
    private fun trySimpleParsing(query: String): ExtractionResult? {
        val lowerQuery = query.lowercase()

        // Паттерны для поиска класса и метода
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

        // Ищем класс
        for (pattern in classPatterns) {
            val match = pattern.find(query)
            if (match != null) {
                className = match.groupValues[1]
                break
            }
        }

        // Ищем метод
        for (pattern in methodPatterns) {
            val match = pattern.find(query)
            if (match != null) {
                methodName = match.groupValues[1]
                break
            }
        }

        // Если ничего не найдено, возвращаем null
        if (className == null && methodName == null) {
            return null
        }

        // [LOG INFO] Если Regex сработал - логируем это, так как это fallback механизм
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
    private fun findNodes(className: String?, methodName: String?): List<Node> {
        val foundNodes = mutableListOf<Node>()

        // Получаем applicationId из контекста или ищем по всем приложениям
        val applicationId = getApplicationIdFromContext()
        val applications = if (applicationId != null) {
            applicationRepository.findById(applicationId).map { listOf(it) }.orElse(emptyList())
        } else {
            // Если applicationId не указан, ищем по всем приложениям
            applicationRepository.findAll()
        }

        if (applications.isEmpty()) {
            log.warn("Не найдено приложений для поиска узлов (Application table empty or ID not found)")
            return foundNodes
        }

        // Если указан и класс, и метод - ищем метод в классе
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
            // Ищем только класс
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
            // Ищем только метод
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
     * Получает applicationId из контекста (если есть)
     */
    private fun getApplicationIdFromContext(): Long? {
        // TODO: добавить поддержку applicationId в QueryProcessingContext
        // Пока что возвращаем null, что означает поиск по всем приложениям
        return null
    }

    /**
     * Результат извлечения информации о классе и методе
     */
    private data class ExtractionResult(
        val className: String?,
        val methodName: String?,
    )
}