package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Шаг классификации намерения запроса.
 * Определяет тип запроса: EXACT (конкретный класс/метод), CONCEPTUAL (семантический), DEFAULT.
 */
@Component
class IntentClassificationStep(
    @param:Qualifier("fastCheckChatClient")
    private val chatClient: ChatClient,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.INTENT_CLASSIFICATION

    override fun execute(context: QueryProcessingContext): StepResult {
        val query = context.currentQuery
        val intent = classifyIntent(query)

        val updatedContext = context
            .setMetadata(QueryMetadataKeys.QUERY_INTENT, intent)
            .addStep(
                ProcessingStep(
                    advisorName = "IntentClassificationStep",
                    input = query,
                    output = "Тип запроса: $intent",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("INTENT_CLASSIFICATION: query='{}', intent={}", query, intent)
        return StepResult(
            context = updatedContext,
            transitionKey = intent,
        )
    }

    private fun classifyIntent(query: String): String {
        // Быстрая эвристика: если в запросе есть CamelCase или явные маркеры — сразу EXACT
        if (hasCamelCaseIdentifier(query) || hasExplicitClassMethodMarker(query)) {
            return "EXACT"
        }

        return try {
            val prompt = """
                Классифицируй запрос пользователя о программном коде. Ответь ОДНИМ словом.

                Типы:
                - EXACT — запрос про конкретный класс, метод или функцию (есть имена в CamelCase, упоминание "класс", "метод", пакет)
                - CONCEPTUAL — семантический вопрос без конкретных имён ("где настройки", "как работает авторизация", "что отвечает за кэширование")
                - DEFAULT — неясный запрос, не относящийся к коду, или слишком общий

                Запрос: $query

                Ответ (EXACT, CONCEPTUAL или DEFAULT):
            """.trimIndent()

            val response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                ?.trim()
                ?.uppercase()
                ?: "DEFAULT"

            when {
                response.contains("EXACT") -> "EXACT"
                response.contains("CONCEPTUAL") -> "CONCEPTUAL"
                else -> "DEFAULT"
            }
        } catch (e: Exception) {
            log.warn("Ошибка при классификации намерения: {}, используем DEFAULT", e.message)
            "DEFAULT"
        }
    }

    private fun hasCamelCaseIdentifier(query: String): Boolean {
        // Ищем CamelCase идентификаторы: слова вида UserService, processData, getUser и т.д.
        return Regex("[A-Z][a-z]+[A-Z][a-zA-Z0-9]*").containsMatchIn(query)
    }

    private fun hasExplicitClassMethodMarker(query: String): Boolean {
        val markers = listOf(
            Regex("\\bкласс\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
            Regex("\\bclass\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
            Regex("\\bметод\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
            Regex("\\bmethod\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
            Regex("\\bфункция\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
            Regex("\\bfunction\\s+[A-Za-z_]", RegexOption.IGNORE_CASE),
        )
        return markers.any { it.containsMatchIn(query) }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "EXACT" to ProcessingStepType.EXTRACTION,
            "CONCEPTUAL" to ProcessingStepType.HYPOTHESIS_GENERATION,
            "DEFAULT" to ProcessingStepType.REWRITING,
        )
    }
}
