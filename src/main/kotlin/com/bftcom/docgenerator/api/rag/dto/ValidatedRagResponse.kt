package com.bftcom.docgenerator.api.rag.dto

import com.bftcom.docgenerator.rag.api.RagResponse

/**
 * Обёртка для RAG ответа с результатами валидации. Validation results идут первыми, затем RAG
 * response.
 */
data class ValidatedRagResponse(
        val validation: EvaluationResult?,
        val ragResponse: RagResponse,
        val validationError: String? = null,
)
