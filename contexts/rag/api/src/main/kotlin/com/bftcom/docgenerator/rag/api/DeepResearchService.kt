package com.bftcom.docgenerator.rag.api

interface DeepResearchService {
    fun research(
        query: String,
        sessionId: String,
        applicationId: Long? = null,
    ): DeepResearchResponse

    fun researchWithProgress(
        query: String,
        sessionId: String,
        applicationId: Long? = null,
        callback: ResearchProgressCallback,
    ): DeepResearchResponse
}

data class DeepResearchResponse(
    val answer: String,
    val thinkingSteps: List<ResearchStep>,
    val sources: List<RagSource>,
    val iterationsUsed: Int,
    val totalDurationMs: Long,
)

data class ResearchStep(
    val iteration: Int,
    val thought: String,
    val action: String?,
    val actionInput: String?,
    val observation: String?,
    val durationMs: Long,
)

fun interface ResearchProgressCallback {
    fun onEvent(event: ResearchEvent)
}

data class ResearchEvent(
    val type: ResearchEventType,
    val iteration: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ResearchEventType {
    THINKING,
    ACTION,
    OBSERVATION,
    ANSWER,
    ERROR,
}
