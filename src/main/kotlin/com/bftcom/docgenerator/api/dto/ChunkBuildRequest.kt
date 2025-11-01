package com.bftcom.docgenerator.api.dto

import java.time.OffsetDateTime

data class ChunkBuildRequest(
    val applicationId: Long,
    val strategy: String, // "per-node"
    val dryRun: Boolean = false, // только посчитать, без записи
    val limitNodes: Long? = null, // лимит узлов на прогон
    val batchSize: Int = 200, // размер страницы чтения нод
    val includeKinds: Set<String>? = null, // фильтр по NodeKind.name (CLASS, METHOD, ...)
    val withEdgesRelations: Boolean = true, // подтягивать рёбра для hint'ов
)

data class ChunkBuildResponse(
    val runId: String,
    val startedAt: OffsetDateTime,
)

data class ChunkBuildStatusDto(
    val runId: String,
    val applicationId: Long,
    val state: String, // running|completed|failed|canceled
    val processedNodes: Long,
    val writtenChunks: Long,
    val skippedChunks: Long,
    val errors: List<String>,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?,
)
