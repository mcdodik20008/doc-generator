package com.bftcom.docgenerator.rag.api

interface RagService {
    fun ask(query: String, sessionId: String, applicationId: Long? = null): RagResponse
    fun prepareContext(query: String, sessionId: String, applicationId: Long? = null): RagPreparedContext

    /**
     * Подготавливает контекст для RAG запроса с real-time отправкой событий о шагах обработки.
     *
     * @param query Запрос пользователя
     * @param sessionId ID сессии
     * @param applicationId ID приложения (optional)
     * @param stepCallback Callback для уведомления о прогрессе шагов
     * @return Подготовленный контекст
     */
    fun prepareContextWithProgress(
        query: String,
        sessionId: String,
        applicationId: Long? = null,
        stepCallback: StepProgressCallback
    ): RagPreparedContext
}

data class RagPreparedContext(
        val prompt: String?,
        val fallbackAnswer: String?,
        val sources: List<RagSource>,
        val metadata: RagQueryMetadata,
        val sessionId: String,
)

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
        val stepType: ProcessingStepType? = null,
        val status: ProcessingStepStatus = ProcessingStepStatus.SUCCESS,
        val durationMs: Long? = null,
)

data class RagSource(
        val id: String,
        val content: String,
        val metadata: Map<String, Any>,
        val similarity: Double,
)
