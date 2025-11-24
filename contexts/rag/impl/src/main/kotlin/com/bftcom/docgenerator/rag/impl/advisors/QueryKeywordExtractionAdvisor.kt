package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.stereotype.Component

/**
 * Advisor для извлечения ключевых слов из запроса.
 * Выделяет важные термины для улучшения поиска.
 */
@Component
class QueryKeywordExtractionAdvisor : QueryProcessingAdvisor {

        override fun getName(): String = "QueryKeywordExtraction"

        override fun getOrder(): Int = 8

        override fun process(context: QueryProcessingContext): Boolean {
                val query = context.currentQuery

                // Извлекаем ключевые слова (убираем стоп-слова)
                val stopWords = setOf(
                        "как", "что", "где", "когда", "почему", "зачем",
                        "какой", "какая", "какое", "какие",
                        "это", "этот", "эта", "эти",
                        "для", "при", "над", "под", "из", "в", "на", "с", "к", "от",
                        "и", "или", "но", "а", "да", "нет",
                        "объясни", "покажи", "найди", "дай",
                )

                val keywords = query
                        .lowercase()
                        .split(Regex("\\s+"))
                        .map { it.trim('?', '!', '.', ',', ';', ':', '(', ')', '[', ']') }
                        .filter { it.isNotBlank() && it.length > 2 && it !in stopWords }
                        .distinct()

                if (keywords.isNotEmpty()) {
                        context.setMetadata(QueryMetadataKeys.KEYWORDS, keywords)
                        context.addStep(
                                ProcessingStep(
                                        advisorName = getName(),
                                        input = query,
                                        output = keywords.joinToString(", "),
                                ),
                        )
                }

                return true
        }
}

