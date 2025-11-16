package com.bftcom.docgenerator.graph.api.events

import java.io.File
import java.nio.file.Path

/**
 * Событие запроса сборки графа для приложения.
 * Используется для асинхронного запуска GraphBuilder.
 */
data class GraphBuildRequestedEvent(
    val applicationId: Long,
    val sourceRoot: Path,
    val classpath: List<File>,
)


