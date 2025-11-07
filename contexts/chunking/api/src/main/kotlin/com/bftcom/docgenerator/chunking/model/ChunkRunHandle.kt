package com.bftcom.docgenerator.chunking.model

import java.time.OffsetDateTime

data class ChunkRunHandle(
    val runId: String,
    val startedAt: OffsetDateTime,
)
