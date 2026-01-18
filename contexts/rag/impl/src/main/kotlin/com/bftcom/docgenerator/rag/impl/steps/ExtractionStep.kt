package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException

@Component
class ExtractionStep(
    @param:Qualifier("fastExtractionChatClient")
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.EXTRACTION

    override fun execute(context: QueryProcessingContext): StepResult {
        val query = context.currentQuery
        val extractionResult = extractClassAndMethod(query)
        val updatedContext = if (extractionResult != null) {
            context
                .setMetadata(
                    QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
                    mapOf(
                        "className" to (extractionResult.className ?: ""),
                        "methodName" to (extractionResult.methodName ?: ""),
                    ),
                )
                .addStep(
                    ProcessingStep(
                        advisorName = "ExtractionStep",
                        input = query,
                        output = "Извлечено: класс='${extractionResult.className}', метод='${extractionResult.methodName}'",
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )
        } else {
            context.addStep(
                ProcessingStep(
                    advisorName = "ExtractionStep",
                    input = query,
                    output = "Не удалось извлечь класс/метод, переходим к REWRITING",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )
        }

        val transitionKey = if (extractionResult?.hasAnyValue() == true) {
            "FOUND"
        } else {
            // Если не удалось извлечь класс/метод, пытаемся переформулировать запрос
            "NOT_FOUND"
        }

        return StepResult(
            context = updatedContext,
            transitionKey = transitionKey,
        )
    }

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

            val response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                ?.trim()
                ?: return trySimpleParsing(query)

            return try {
                val json = cleanJsonResponse(response)
                val map = objectMapper.readValue(json, Map::class.java) as Map<*, *>
                val className = (map["className"] as? String)?.takeIf { it.isNotBlank() }
                val methodName = (map["methodName"] as? String)?.takeIf { it.isNotBlank() }
                if (className == null && methodName == null) {
                    trySimpleParsing(query)
                } else {
                    ExtractionResult(className, methodName)
                }
            } catch (e: Exception) {
                log.info("Не удалось распарсить ответ LLM, переключаемся на простой парсинг. Ошибка: {}", e.message)
                trySimpleParsing(query)
            }
        } catch (e: ResourceAccessException) {
            val cause = e.cause
            if (cause is SocketTimeoutException || cause is TimeoutException) {
                log.warn("Таймаут при извлечении класса/метода через LLM, используем Regex fallback.")
            } else {
                log.warn("Ошибка доступа к LLM при извлечении класса/метода: {}", e.message)
            }
            return trySimpleParsing(query)
        } catch (e: SocketTimeoutException) {
            log.warn("Таймаут при извлечении класса/метода через LLM, используем Regex fallback.")
            return trySimpleParsing(query)
        } catch (e: Exception) {
            log.warn("Неожиданная ошибка при извлечении через LLM: {}", e.message)
            return trySimpleParsing(query)
        }
    }

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

        val className = classPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(query)?.groupValues?.getOrNull(1)
        }
        val methodName = methodPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(query)?.groupValues?.getOrNull(1)
        }

        if (className == null && methodName == null) {
            return null
        }

        return ExtractionResult(className, methodName)
    }

    private fun cleanJsonResponse(response: String): String {
        return response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "FOUND" to ProcessingStepType.EXACT_SEARCH,
            "NOT_FOUND" to ProcessingStepType.REWRITING,
        )
    }

    private data class ExtractionResult(
        val className: String?,
        val methodName: String?,
    ) {
        fun hasAnyValue(): Boolean = !className.isNullOrBlank() || !methodName.isNullOrBlank()
    }
}
