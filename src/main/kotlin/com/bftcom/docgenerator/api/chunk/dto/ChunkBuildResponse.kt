package com.bftcom.docgenerator.api.chunk.dto

import java.time.OffsetDateTime

data class ChunkBuildResponse(
    val runId: String,
    val startedAt: OffsetDateTime,
)
