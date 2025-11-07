package com.bftcom.docgenerator.chunking.dto

import java.time.OffsetDateTime

data class ChunkBuildResponse(
    val runId: String,
    val startedAt: OffsetDateTime,
)
