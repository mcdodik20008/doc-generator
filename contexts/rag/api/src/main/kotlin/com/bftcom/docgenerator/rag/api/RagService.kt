package com.bftcom.docgenerator.rag.api

interface RagService {
    fun ask(query: String): RagResponse
}

data class RagResponse(
    val answer: String,
    val sources: List<RagSource>,
)

data class RagSource(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
    val similarity: Double,
)
