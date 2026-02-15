package com.bftcom.docgenerator.api.ingest.dto

import java.time.OffsetDateTime

data class IngestRunDto(
    val runId: Long,
    val applicationId: Long,
    val status: String,
    val triggeredBy: String?,
    val branch: String?,
    val commitSha: String?,
    val errorMessage: String?,
    val steps: List<IngestStepDto>,
    val startedAt: OffsetDateTime?,
    val finishedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)
