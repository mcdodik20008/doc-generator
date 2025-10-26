package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.api.dto.ChunkBuildStatusDto
import com.bftcom.docgenerator.chunking.api.ChunkRunStore
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory реализация хранилища статусов выполнения построения чанков.
 *
 * Потокобезопасная, подходит для отладки и single-instance режима.
 */
@Service
class InMemoryChunkRunStore : ChunkRunStore {
    private val runs = ConcurrentHashMap<String, InternalRun>()

    override fun create(
        applicationId: Long,
        strategy: String,
    ): ChunkRunStore.Run {
        val runId = UUID.randomUUID().toString()
        val started = OffsetDateTime.now()
        runs[runId] =
            InternalRun(
                runId = runId,
                applicationId = applicationId,
                strategy = strategy,
                state = "created",
                processed = 0,
                written = 0,
                skipped = 0,
                errors = mutableListOf(),
                startedAt = started,
                finishedAt = null,
            )
        return ChunkRunStore.Run(runId, started)
    }

    override fun markRunning(runId: String) {
        runs[runId]?.apply { state = "running" }
    }

    override fun markCompleted(
        runId: String,
        processed: Long,
        written: Long,
        skipped: Long,
    ) {
        runs[runId]?.apply {
            state = "completed"
            this.processed = processed
            this.written = written
            this.skipped = skipped
            finishedAt = OffsetDateTime.now()
        }
    }

    override fun markFailed(
        runId: String,
        e: Exception,
    ) {
        runs[runId]?.apply {
            state = "failed"
            errors += e.message ?: e.javaClass.simpleName
            finishedAt = OffsetDateTime.now()
        }
    }

    override fun status(runId: String): ChunkBuildStatusDto {
        val run = runs[runId] ?: error("Run not found: $runId")
        return ChunkBuildStatusDto(
            runId = run.runId,
            applicationId = run.applicationId,
            state = run.state,
            processedNodes = run.processed,
            writtenChunks = run.written,
            skippedChunks = run.skipped,
            errors = run.errors.toList(),
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
        )
    }

    private data class InternalRun(
        val runId: String,
        val applicationId: Long,
        val strategy: String,
        var state: String,
        var processed: Long,
        var written: Long,
        var skipped: Long,
        val errors: MutableList<String>,
        val startedAt: OffsetDateTime,
        var finishedAt: OffsetDateTime?,
    )
}
