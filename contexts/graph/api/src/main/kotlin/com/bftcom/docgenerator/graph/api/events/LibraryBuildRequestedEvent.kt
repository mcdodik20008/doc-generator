package com.bftcom.docgenerator.graph.api.events

import java.io.File

/**
 * Событие: нужно построить граф библиотек (анализ classpath/jar).
 * После обработки слушатель может инициировать построение графа приложения.
 */
data class LibraryBuildRequestedEvent(
    val applicationId: Long,
    val classpath: List<File>,
)
