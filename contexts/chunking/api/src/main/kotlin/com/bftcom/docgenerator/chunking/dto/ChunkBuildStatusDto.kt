package com.bftcom.docgenerator.chunking.dto

import java.time.OffsetDateTime

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
