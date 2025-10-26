package com.bftcom.docgenerator.api.dto

import com.bftcom.docgenerator.domain.enums.NodeKind
import java.time.OffsetDateTime

data class ChunkBuildRequest(
    val applicationId: Long,
    val strategy: String = "per-node", // имя стратегии чанкования
    val dryRun: Boolean = false, // не писать в БД, только посчитать
    val limitNodes: Int? = null, // ограничение по кол-ву нод
    val batchSize: Int = 500, // размер страницы по нодам
    val includeKinds: Set<NodeKind>? = null, // фильтр по видам нод
    val withEdgesRelations: Boolean = true, // выписывать relations в чанки из edges
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
