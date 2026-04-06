package com.bftcom.docgenerator.rag.api

/**
 * Событие о выполнении шага RAG pipeline.
 * Отправляется клиенту в real-time через SSE.
 */
data class StepEvent(
    /**
     * Тип шага FSM
     */
    val stepType: ProcessingStepType,
    /**
     * Статус выполнения шага
     */
    val status: StepEventStatus,
    /**
     * Человекочитаемое описание текущего действия
     */
    val description: String,
    /**
     * Дополнительные данные (опционально)
     */
    val metadata: Map<String, Any> = emptyMap(),
    /**
     * Время начала шага
     */
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Статус события шага
 */
enum class StepEventStatus {
    /**
     * Шаг начал выполнение
     */
    STARTED,

    /**
     * Шаг выполняется (промежуточное обновление)
     */
    IN_PROGRESS,

    /**
     * Шаг успешно завершен
     */
    COMPLETED,

    /**
     * Шаг завершен с ошибкой
     */
    FAILED,

    /**
     * Шаг пропущен
     */
    SKIPPED,
}

/**
 * Callback интерфейс для уведомления о шагах обработки
 */
fun interface StepProgressCallback {
    fun onStepUpdate(event: StepEvent)
}
