package com.bftcom.docgenerator.chunking.api

import com.bftcom.docgenerator.chunking.dto.ChunkBuildStatusDto
import java.time.OffsetDateTime

interface ChunkRunStore {
    fun create(
        applicationId: Long,
        strategy: String,
    ): Run

    fun markRunning(runId: String)

    fun markCompleted(
        runId: String,
        processed: Long,
        written: Long,
        skipped: Long,
    )

    fun markFailed(
        runId: String,
        e: Exception,
    )

    fun status(runId: String): ChunkBuildStatusDto

    data class Run(
        val runId: String,
        val startedAt: OffsetDateTime,
    )
}
