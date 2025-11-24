package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Advisor для расширения запроса (query expansion).
 * Генерирует дополнительные варианты запроса для более широкого поиска.
 */
@Component
class QueryExpansionAdvisor(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryProcessingAdvisor {

    override fun getName(): String = "QueryExpansion"

    override fun getOrder(): Int = 20

    override fun process(context: QueryProcessingContext): Boolean {
        val currentQuery = context.currentQuery

        // Пропускаем расширение, если уже было выполнено
        if (context.hasMetadata(QueryMetadataKeys.EXPANDED)) {
            return true
        }

        val expansionPrompt = """
                        Для следующего запроса сгенерируй 2-3 альтернативные формулировки, которые могут помочь найти релевантную информацию.
                        Каждая формулировка должна быть на отдельной строке.
                        Используй синонимы, связанные термины и альтернативные способы выражения того же вопроса.
                        
                        Оригинальный запрос: $currentQuery
                        
                        Альтернативные формулировки (по одной на строку):
                """.trimIndent()

        val expansionResult = chatClient
            .prompt()
            .user(expansionPrompt)
            .call()
            .content()
            ?.trim()
            ?: ""

        val expandedQueries = expansionResult
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != currentQuery }
            .take(3)

        if (expandedQueries.isNotEmpty()) {
            context.setMetadata(QueryMetadataKeys.EXPANDED, true)
            context.setMetadata(QueryMetadataKeys.EXPANDED_QUERIES, expandedQueries)
            context.addStep(
                ProcessingStep(
                    advisorName = getName(),
                    input = currentQuery,
                    output = expandedQueries.joinToString("\n"),
                ),
            )
        }

        return true
    }
}

