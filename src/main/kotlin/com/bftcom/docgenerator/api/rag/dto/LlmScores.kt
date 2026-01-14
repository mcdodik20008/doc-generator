package com.bftcom.docgenerator.api.rag.dto

/** Оценки от LLM судей. */
data class LlmScores(
        val gigachat: Double? = null,
        val gemini: Double? = null,
        val ollama: Double? = null,
)