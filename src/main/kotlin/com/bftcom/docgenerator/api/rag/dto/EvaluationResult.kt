package com.bftcom.docgenerator.api.rag.dto

import com.fasterxml.jackson.annotation.JsonProperty

/** Результат оценки документации от doc-evaluator сервиса. */
data class EvaluationResult(
    // Локальные метрики
    @JsonProperty("semantic_score") val semanticScore: Double,
    @JsonProperty("keyword_coverage") val keywordCoverage: Double,
    @JsonProperty("readability_score") val readabilityScore: Double,

    // LLM метрики
    @JsonProperty("llm_scores") val llmScores: LlmScores,

    // Итоговые метрики
    @JsonProperty("final_score") val finalScore: Double,
    @JsonProperty("score_variance") val scoreVariance: Double,
    @JsonProperty("confidence_score") val confidenceScore: Double,
)
