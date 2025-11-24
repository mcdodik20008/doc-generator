package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.stereotype.Component

/**
 * Advisor для нормализации запроса.
 * Удаляет лишние пробелы, приводит к единому формату, исправляет опечатки.
 */
@Component
class QueryNormalizationAdvisor : QueryProcessingAdvisor {

        override fun getName(): String = "QueryNormalization"

        override fun getOrder(): Int = 5

        override fun process(context: QueryProcessingContext): Boolean {
                val originalQuery = context.currentQuery
                var normalizedQuery = originalQuery

                // Нормализация пробелов
                normalizedQuery = normalizedQuery
                        .replace(Regex("\\s+"), " ")
                        .trim()

                // Удаление лишних знаков препинания в конце
                normalizedQuery = normalizedQuery.trimEnd('?', '!', '.', ',', ';', ':')

                if (normalizedQuery != originalQuery) {
                        context.updateQuery(normalizedQuery)
                        context.setMetadata(QueryMetadataKeys.NORMALIZED, true)
                        context.addStep(
                                ProcessingStep(
                                        advisorName = getName(),
                                        input = originalQuery,
                                        output = normalizedQuery,
                                ),
                        )
                }

                return true
        }
}

