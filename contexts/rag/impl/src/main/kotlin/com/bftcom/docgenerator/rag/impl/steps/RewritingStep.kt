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
 * Шаг переформулировки запроса.
 * Улучшает формулировку запроса для лучшего поиска релевантной информации.
 */
@Component
class RewritingStep(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.REWRITING

    override fun execute(context: QueryProcessingContext): StepResult {
        val originalQuery = context.currentQuery

        // Пропускаем переформулировку, если запрос уже был переформулирован
        if (context.hasMetadata(QueryMetadataKeys.REWRITTEN)) {
            log.debug("Запрос уже переформулирован, пропускаем шаг REWRITING")
            return StepResult(
                context = context,
                transitionKey = "SUCCESS",
            )
        }

        val rewritePrompt = """
            Переформулируй следующий запрос пользователя для лучшего поиска информации в технической документации и коде.
            
            ВАЖНО:
            - ОБЯЗАТЕЛЬНО сохрани все точные названия классов, методов, функций, переменных БЕЗ ИЗМЕНЕНИЙ
            - Сохрани смысл и ключевые термины
            - Сделай запрос более точным и подходящим для поиска, но НЕ меняй технические названия
            - Если в запросе есть названия в CamelCase или с цифрами, сохрани их точно
            
            Ответь ТОЛЬКО переформулированным запросом, без дополнительных комментариев.
            
            Оригинальный запрос: $originalQuery
            
            Переформулированный запрос:
        """.trimIndent()

        val rewrittenQuery = try {
            chatClient
                .prompt()
                .user(rewritePrompt)
                .call()
                .content()
                ?.trim()
                ?: originalQuery
        } catch (e: Exception) {
            log.warn("Ошибка при переформулировке запроса: {}", e.message)
            originalQuery
        }

        val updatedContext = if (rewrittenQuery != originalQuery) {
            context
                .updateQuery(rewrittenQuery)
                .setMetadata(QueryMetadataKeys.REWRITTEN, true)
                .setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, rewrittenQuery)
                .addStep(
                    ProcessingStep(
                        advisorName = "RewritingStep",
                        input = originalQuery,
                        output = rewrittenQuery,
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )
        } else {
            context
        }

        // После переформулировки переходим к EXPANSION
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.EXPANSION,
        )
    }
}
