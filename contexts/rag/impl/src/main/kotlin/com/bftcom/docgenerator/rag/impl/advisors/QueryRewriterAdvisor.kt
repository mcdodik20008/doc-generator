package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Advisor для переформулировки запроса пользователя перед RAG поиском.
 * Улучшает формулировку запроса для лучшего поиска релевантной информации.
 */
@Component
class QueryRewriterAdvisor(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryProcessingAdvisor {

    override fun getName(): String = "QueryRewriter"

    override fun getOrder(): Int = 10

    override fun process(context: QueryProcessingContext): Boolean {
        val originalQuery = context.currentQuery

        // Пропускаем переформулировку, если запрос уже был переформулирован
        if (context.hasMetadata(QueryMetadataKeys.REWRITTEN)) {
            return true
        }

        val rewritePrompt = """
                        Переформулируй следующий запрос пользователя для лучшего поиска информации в технической документации и коде.
                        Сохрани смысл и ключевые термины, но сделай запрос более точным и подходящим для поиска.
                        Ответь ТОЛЬКО переформулированным запросом, без дополнительных комментариев.
                        
                        Оригинальный запрос: $originalQuery
                        
                        Переформулированный запрос:
                """.trimIndent()

        val rewrittenQuery = chatClient
            .prompt()
            .user(rewritePrompt)
            .call()
            .content()
            ?.trim()
            ?: originalQuery

        if (rewrittenQuery != originalQuery) {
            context.updateQuery(rewrittenQuery)
            context.setMetadata(QueryMetadataKeys.REWRITTEN, true)
            context.setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, rewrittenQuery)
            context.addStep(
                ProcessingStep(
                    advisorName = getName(),
                    input = originalQuery,
                    output = rewrittenQuery,
                ),
            )
        }

        return true
    }
}

