package com.bftcom.docgenerator.api.ingest.dto

import java.time.OffsetDateTime

data class IngestEventDto(
    val eventId: Long,
    val runId: Long,
    val stepType: String?,
    val level: String,
    val message: String,
    val context: Map<String, Any>?,
    val createdAt: OffsetDateTime,
)
