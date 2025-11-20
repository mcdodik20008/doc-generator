package com.bftcom.docgenerator.api.embedding.dto

data class SearchResultResponse(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
    val similarity: Double,
)

