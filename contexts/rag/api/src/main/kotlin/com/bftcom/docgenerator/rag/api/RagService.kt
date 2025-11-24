package com.bftcom.docgenerator.rag.api

interface RagService {
    fun ask(query: String, sessionId: String): RagResponse
}

data class RagResponse(
        val answer: String,
        val sources: List<RagSource>,
        val metadata: RagQueryMetadata = RagQueryMetadata(),
)

data class RagQueryMetadata(
        val originalQuery: String = "",
        val rewrittenQuery: String? = null,
        val expandedQueries: List<String> = emptyList(),
        val processingSteps: List<ProcessingStep> = emptyList(),
        val additionalData: Map<String, Any> = emptyMap(),
)

data class ProcessingStep(
        val advisorName: String,
        val input: String,
        val output: String,
        val timestamp: Long = System.currentTimeMillis(),
)

data class RagSource(
        val id: String,
        val content: String,
        val metadata: Map<String, Any>,
        val similarity: Double,
)
