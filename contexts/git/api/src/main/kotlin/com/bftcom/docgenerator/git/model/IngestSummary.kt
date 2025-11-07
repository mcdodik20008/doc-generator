package com.bftcom.docgenerator.git.model

import java.time.OffsetDateTime

data class IngestSummary(
    val appKey: String,
    val repoPath: String,
    val headSha: String?,
    val nodes: Int,
    val edges: Int,
    val startedAt: OffsetDateTime,
    val finishedAt: OffsetDateTime,
    val tookMs: Long,
)
