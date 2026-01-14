package com.bftcom.docgenerator.graph.impl.linker

import java.time.Duration
import java.time.OffsetDateTime

/**
 * Статистика процесса линковки графа. Содержит детальную информацию о времени выполнения каждого
 * этапа и использовании ресурсов.
 */
data class LinkingStats(
        val totalNodes: Int,
        val totalEdges: Int,
        val newIntegrationNodes: Int,
        val libraryNodeEdges: Int,
        val callsErrors: Int,
        val structuralLinkingDuration: Duration,
        val parallelLinkingDuration: Duration,
        val indexUpdateDuration: Duration,
        val persistenceDuration: Duration,
        val totalDuration: Duration,
        val memoryUsedMb: Long,
        val startedAt: OffsetDateTime,
        val finishedAt: OffsetDateTime,
) {
    /** Форматирует статистику в читаемую строку для логирования. */
    fun toLogString(): String =
            """
        |Linking completed:
        |  - Total nodes processed: $totalNodes
        |  - Total edges created: $totalEdges
        |  - New integration nodes: $newIntegrationNodes
        |  - Library node edges: $libraryNodeEdges
        |  - Calls errors: $callsErrors
        |  - Structural linking: ${structuralLinkingDuration.toMillis()}ms
        |  - Parallel linking: ${parallelLinkingDuration.toMillis()}ms
        |  - Index update: ${indexUpdateDuration.toMillis()}ms
        |  - Persistence: ${persistenceDuration.toMillis()}ms
        |  - Total duration: ${totalDuration.toMillis()}ms
        |  - Memory used: ${memoryUsedMb}MB
        """.trimMargin()
}
