package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.stereotype.Component

/**
 * Цепочка обработки запроса перед RAG.
 * Выполняет advisors в порядке их приоритета.
 */
@Component
class QueryProcessingChain(
        private val advisors: List<QueryProcessingAdvisor>,
) {
        /**
         * Обрабатывает запрос через цепочку advisors
         */
        fun process(originalQuery: String, sessionId: String): QueryProcessingContext {
                val context = QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                )

                // Выполняем advisors в порядке приоритета
                advisors.sortedBy { it.getOrder() }.forEach { advisor ->
                        try {
                                val shouldContinue = advisor.process(context)
                                if (!shouldContinue) {
                                        // Advisor решил прервать цепочку
                                        return context
                                }
                        } catch (e: Exception) {
                                // Логируем ошибку, но продолжаем цепочку
                                val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${advisor.getName()}"
                                context.metadata[errorKey] = e.message ?: "Unknown error"
                        }
                }

                return context
        }
}

