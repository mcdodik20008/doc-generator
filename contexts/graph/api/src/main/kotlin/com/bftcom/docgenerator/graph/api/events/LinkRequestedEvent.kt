package com.bftcom.docgenerator.graph.api.events

/**
 * Событие: нужно выполнить линковку графа (построение edges между нодами внутри приложения).
 * Обычно публикуется после успешной сборки графа (GraphBuildRequestedEvent).
 */
data class LinkRequestedEvent(
    val applicationId: Long,
)
