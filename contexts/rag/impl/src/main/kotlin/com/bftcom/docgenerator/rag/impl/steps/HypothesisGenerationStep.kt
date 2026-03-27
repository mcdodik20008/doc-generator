package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Шаг генерации гипотез для концептуальных запросов.
 * Генерирует вероятные имена классов/методов и гипотетический код (HyDE)
 * для семантических запросов без конкретных имён.
 */
@Component
class HypothesisGenerationStep(
    @param:Qualifier("coderChatClient")
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.HYPOTHESIS_GENERATION

    override fun execute(context: QueryProcessingContext): StepResult {
        val query = context.currentQuery
        val hypothesis = generateHypothesis(query)

        var updatedContext = context

        if (hypothesis != null) {
            updatedContext = updatedContext
                .setMetadata(QueryMetadataKeys.HYPOTHETICAL_NAMES, hypothesis.names)
                .setMetadata(QueryMetadataKeys.HYPOTHETICAL_CODE, hypothesis.code)

            // Заполняем EXACT_NODE_SEARCH_RESULT с первым гипотетическим именем для ExactSearchStep
            if (hypothesis.names.isNotEmpty()) {
                updatedContext = updatedContext.setMetadata(
                    QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
                    mapOf(
                        "className" to hypothesis.names.first(),
                        "methodName" to "",
                    ),
                )
            }
        }

        updatedContext = updatedContext.addStep(
            ProcessingStep(
                advisorName = "HypothesisGenerationStep",
                input = query,
                output = if (hypothesis != null) {
                    "Гипотезы: ${hypothesis.names.joinToString(", ")}"
                } else {
                    "Не удалось сгенерировать гипотезы"
                },
                stepType = type,
                status = ProcessingStepStatus.SUCCESS,
            ),
        )

        log.info("HYPOTHESIS_GENERATION: query='{}', names={}", query, hypothesis?.names)
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    private fun generateHypothesis(query: String): HypothesisResult? {
        return try {
            val prompt = """
                Ты — эксперт по Java/Kotlin/Spring. Для запроса пользователя сгенерируй гипотезы о том, какие классы и методы могут быть связаны с этим запросом в типичном Spring Boot проекте.

                Запрос: "$query"

                Ответь СТРОГО в формате JSON:
                {
                  "names": ["ClassName1", "ClassName2", "methodName1", "ServiceName"],
                  "code": "// Гипотетический фрагмент кода, который мог бы реализовать описанное\n@Service\nclass ExampleService {\n    fun exampleMethod() { ... }\n}"
                }

                Правила:
                - names: 3-5 вероятных имён классов, сервисов, методов в CamelCase
                - code: короткий гипотетический фрагмент кода (5-15 строк), который мог бы существовать в проекте
                - Используй типичные Spring паттерны (Service, Repository, Controller, Config)
                - Ответь ТОЛЬКО JSON, без пояснений
            """.trimIndent()

            val response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                ?.trim()
                ?: return null

            parseHypothesis(response)
        } catch (e: Exception) {
            log.warn("Ошибка при генерации гипотез: {}", e.message)
            null
        }
    }

    private fun parseHypothesis(response: String): HypothesisResult? {
        return try {
            val cleaned = response
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val map = objectMapper.readValue(cleaned, Map::class.java) as Map<*, *>

            val names = (map["names"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val code = (map["code"] as? String)?.takeIf { it.isNotBlank() } ?: ""

            if (names.isEmpty() && code.isBlank()) {
                null
            } else {
                HypothesisResult(names = names, code = code)
            }
        } catch (e: Exception) {
            log.warn("Не удалось распарсить ответ гипотез: {}", e.message)
            null
        }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.EXACT_SEARCH,
        )
    }

    private data class HypothesisResult(
        val names: List<String>,
        val code: String,
    )
}
