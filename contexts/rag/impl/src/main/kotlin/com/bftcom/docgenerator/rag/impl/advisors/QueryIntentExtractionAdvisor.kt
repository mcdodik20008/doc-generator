package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Advisor для извлечения намерения из запроса.
 * Определяет тип запроса (поиск кода, объяснение, пример использования и т.д.)
 */
@Component
class QueryIntentExtractionAdvisor(
    @param:Qualifier("ragChatClient")
        private val chatClient: ChatClient,
) : QueryProcessingAdvisor {

        override fun getName(): String = "QueryIntentExtraction"

        override fun getOrder(): Int = 15

        override fun process(context: QueryProcessingContext): Boolean {
                val query = context.currentQuery

                // Пропускаем, если намерение уже извлечено
                if (context.hasMetadata(QueryMetadataKeys.INTENT)) {
                        return true
                }

                val intentPrompt = """
                        Определи тип намерения пользователя в следующем запросе.
                        Выбери ОДИН из типов: CODE_SEARCH, EXPLANATION, USAGE_EXAMPLE, API_REFERENCE, TROUBLESHOOTING, OTHER
                        Ответь ТОЛЬКО одним словом - типом намерения.
                        
                        Запрос: $query
                        
                        Тип намерения:
                """.trimIndent()

                val intent = chatClient
                        .prompt()
                        .user(intentPrompt)
                        .call()
                        .content()
                        ?.trim()
                        ?.uppercase()
                        ?: "OTHER"

                context.setMetadata(QueryMetadataKeys.INTENT, intent)
                context.addStep(
                        ProcessingStep(
                                advisorName = getName(),
                                input = query,
                                output = intent,
                        ),
                )

                return true
        }
}

