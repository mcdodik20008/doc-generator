package com.bftcom.docgenerator.ingest

import java.time.OffsetDateTime

data class IngestSummary(
    val appKey: String,
    val repoPath: String,
    val headSha: String?,
    val nodes: Int,
    val edges: Int,
    val chunks: Int,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime,
    val tookMs: Long,
)
