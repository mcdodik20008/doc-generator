package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemory.DEFAULT_CONVERSATION_ID
import org.springframework.stereotype.Component

/**
 * Advisor для обогащения запроса контекстом из истории разговора.
 * Использует предыдущие сообщения для улучшения понимания запроса.
 */
@Component
class QueryContextEnrichmentAdvisor(
        private val chatMemory: ChatMemory,
) : QueryProcessingAdvisor {

        override fun getName(): String = "QueryContextEnrichment"

        override fun getOrder(): Int = 25

        override fun process(context: QueryProcessingContext): Boolean {
                 try {
                        // Получаем историю разговора
                        val conversationId = DEFAULT_CONVERSATION_ID
                        val messages = chatMemory.get(conversationId)

                        if (messages.isNotEmpty()) {
                                // Берем последние 2-3 сообщения для контекста
                                val recentMessages =
                                    messages.takeLast(3).joinToString("\n") { "${it.messageType}: ${it.text}" }

                            context.setMetadata(QueryMetadataKeys.CONVERSATION_HISTORY, recentMessages)
                                context.setMetadata(QueryMetadataKeys.HAS_HISTORY, true)

                                context.addStep(
                                        ProcessingStep(
                                                advisorName = getName(),
                                                input = context.currentQuery,
                                                output = "Enriched with ${recentMessages.length} chars of history",
                                        ),
                                )
                        } else {
                                context.setMetadata(QueryMetadataKeys.HAS_HISTORY, false)
                        }
                } catch (e: Exception) {
                        // Если не удалось получить историю, просто пропускаем
                        context.setMetadata(QueryMetadataKeys.HISTORY_ERROR, e.message ?: "Unknown error")
                }

                return true
        }
}

