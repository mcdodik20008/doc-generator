package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestEventDto
import com.bftcom.docgenerator.api.ingest.dto.IngestRunDto
import com.bftcom.docgenerator.api.ingest.dto.IngestStepDto
import com.bftcom.docgenerator.domain.ingest.IngestEvent
import com.bftcom.docgenerator.domain.ingest.IngestRun
import com.bftcom.docgenerator.domain.ingest.IngestStep

object IngestDtoMapper {
    fun IngestRun.toDto(steps: List<IngestStep> = emptyList()): IngestRunDto =
        IngestRunDto(
            runId = id!!,
            applicationId = application.id!!,
            status = status,
            triggeredBy = triggeredBy,
            branch = branch,
            commitSha = commitSha,
            errorMessage = errorMessage,
            steps = steps.map { it.toDto() },
            startedAt = startedAt,
            finishedAt = finishedAt,
            createdAt = createdAt,
        )

    fun IngestStep.toDto(): IngestStepDto =
        IngestStepDto(
            stepType = stepType,
            status = status,
            itemsProcessed = itemsProcessed,
            itemsTotal = itemsTotal,
            errorMessage = errorMessage,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )

    fun IngestEvent.toDto(): IngestEventDto =
        IngestEventDto(
            eventId = id!!,
            runId = run.id!!,
            stepType = stepType,
            level = level,
            message = message,
            context = context,
            createdAt = createdAt,
        )
}
