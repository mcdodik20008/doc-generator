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
 * Шаг расширения запроса (query expansion).
 * Генерирует дополнительные варианты запроса для более широкого поиска.
 */
@Component
class ExpansionStep(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.EXPANSION

    override fun execute(context: QueryProcessingContext): StepResult {
        val currentQuery = context.currentQuery

        // Пропускаем расширение, если уже было выполнено
        if (context.hasMetadata(QueryMetadataKeys.EXPANDED)) {
            log.debug("Запрос уже расширен, пропускаем шаг EXPANSION")
            return StepResult(
                context = context,
                transitionKey = "SUCCESS",
            )
        }

        val expansionPrompt = """
            Сгенерируй 3-5 альтернативных формулировок следующего запроса для поиска в технической документации.
            Каждая формулировка должна быть на отдельной строке.
            Сохраняй точные названия классов, методов и технических терминов БЕЗ ИЗМЕНЕНИЙ.
            Не включай оригинальный запрос в список.
            Не добавляй нумерацию или маркеры.
            
            Оригинальный запрос: $currentQuery
            
            Альтернативные формулировки:
        """.trimIndent()

        val expansionResponse = try {
            chatClient
                .prompt()
                .user(expansionPrompt)
                .call()
                .content()
                ?.trim()
        } catch (e: Exception) {
            log.warn("Ошибка при расширении запроса: {}", e.message)
            null
        }

        val updatedContext = if (expansionResponse != null && expansionResponse.isNotBlank()) {
            val expandedQueries = expansionResponse
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != currentQuery }
                .distinct()
                .take(5) // Ограничиваем до 5 вариантов

            if (expandedQueries.isNotEmpty()) {
                context
                    .setMetadata(QueryMetadataKeys.EXPANDED, true)
                    .setMetadata(QueryMetadataKeys.EXPANDED_QUERIES, expandedQueries)
                    .addStep(
                        ProcessingStep(
                            advisorName = "ExpansionStep",
                            input = currentQuery,
                            output = "Сгенерировано ${expandedQueries.size} альтернативных формулировок",
                            stepType = type,
                            status = ProcessingStepStatus.SUCCESS,
                        ),
                    )
            } else {
                context
            }
        } else {
            context
        }

        // После расширения переходим к VECTOR_SEARCH
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.VECTOR_SEARCH,
        )
    }
}
