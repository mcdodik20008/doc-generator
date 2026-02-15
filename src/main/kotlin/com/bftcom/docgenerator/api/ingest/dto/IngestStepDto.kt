package com.bftcom.docgenerator.api.ingest.dto

import java.time.OffsetDateTime

data class IngestStepDto(
    val stepType: String,
    val status: String,
    val itemsProcessed: Int?,
    val itemsTotal: Int?,
    val errorMessage: String?,
    val startedAt: OffsetDateTime?,
    val finishedAt: OffsetDateTime?,
)
