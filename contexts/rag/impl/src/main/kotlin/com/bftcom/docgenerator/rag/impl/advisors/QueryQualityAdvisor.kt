package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.stereotype.Component

/**
 * Advisor для оценки качества запроса.
 * Проверяет длину, наличие ключевых слов и другие метрики.
 */
@Component
class QueryQualityAdvisor : QueryProcessingAdvisor {

        override fun getName(): String = "QueryQuality"

        override fun getOrder(): Int = 7

        override fun process(context: QueryProcessingContext): Boolean {
                val query = context.currentQuery

                val qualityMetrics = mutableMapOf<String, Any>()

                // Длина запроса
                qualityMetrics["length"] = query.length
                qualityMetrics["wordCount"] = query.split(Regex("\\s+")).size

                // Наличие вопросительных слов
                val questionWords = listOf("как", "что", "где", "когда", "почему", "зачем", "какой")
                val hasQuestionWord = questionWords.any { query.lowercase().contains(it) }
                qualityMetrics["hasQuestionWord"] = hasQuestionWord

                // Наличие технических терминов (простой эвристический подход)
                val techIndicators = listOf("класс", "метод", "функция", "api", "endpoint", "сервис", "компонент")
                val techTermCount = techIndicators.count { query.lowercase().contains(it) }
                qualityMetrics["techTermCount"] = techTermCount

                // Оценка качества (0-1)
                val qualityScore = calculateQualityScore(qualityMetrics)
                qualityMetrics["qualityScore"] = qualityScore

                context.setMetadata(QueryMetadataKeys.QUALITY_METRICS, qualityMetrics)

                context.addStep(
                        ProcessingStep(
                                advisorName = getName(),
                                input = query,
                                output = "Quality score: $qualityScore",
                        ),
                )

                return true
        }

        private fun calculateQualityScore(metrics: Map<String, Any>): Double {
                var score = 0.5 // Базовый score

                val length = metrics["length"] as? Int ?: 0
                val wordCount = metrics["wordCount"] as? Int ?: 0
                val hasQuestionWord = metrics["hasQuestionWord"] as? Boolean ?: false
                val techTermCount = metrics["techTermCount"] as? Int ?: 0

                // Оптимальная длина запроса: 20-100 символов
                when {
                        length in 20..100 -> score += 0.2
                        length < 10 -> score -= 0.2
                        length > 200 -> score -= 0.1
                }

                // Оптимальное количество слов: 3-15
                when {
                        wordCount in 3..15 -> score += 0.1
                        wordCount < 2 -> score -= 0.2
                }

                // Наличие вопросительного слова - хорошо
                if (hasQuestionWord) score += 0.1

                // Технические термины - хорошо
                if (techTermCount > 0) score += 0.1 * techTermCount.coerceAtMost(3)

                return score.coerceIn(0.0, 1.0)
        }
}

