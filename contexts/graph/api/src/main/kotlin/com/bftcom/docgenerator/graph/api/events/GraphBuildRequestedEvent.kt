package com.bftcom.docgenerator.graph.api.events

import java.io.File
import java.nio.file.Path

/**
 * Событие: нужно построить граф кода приложения (Node/Edge) по исходникам.
 * Обычно публикуется после того, как библиотеки уже проанализированы.
 */
data class GraphBuildRequestedEvent(
    val applicationId: Long,
    val sourceRoot: Path,
    val classpath: List<File>,
)
