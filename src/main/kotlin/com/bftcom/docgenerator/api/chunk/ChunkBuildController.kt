package com.bftcom.docgenerator.api.chunk

import com.bftcom.docgenerator.chunking.api.ChunkBuildOrchestrator
import com.bftcom.docgenerator.chunking.dto.ChunkBuildRequest
import com.bftcom.docgenerator.chunking.dto.ChunkBuildResponse
import com.bftcom.docgenerator.chunking.dto.ChunkBuildStatusDto
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal/chunks")
class ChunkBuildController(
    private val orchestrator: ChunkBuildOrchestrator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/run")
    fun run(
        @RequestBody @Valid req: ChunkBuildRequest,
    ): ResponseEntity<ChunkBuildResponse> {
        log.info("Chunk build requested: applicationId={}, strategy={}, dryRun={}", req.applicationId, req.strategy, req.dryRun)
        return try {
            val run = orchestrator.start(req)
            log.info("Chunk build started: runId={}", run.runId)
            ResponseEntity.ok(ChunkBuildResponse(runId = run.runId, startedAt = run.startedAt))
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid chunk build request: {}", e.message)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        } catch (e: IllegalStateException) {
            log.warn("Invalid chunk build state: {}", e.message)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        } catch (e: Exception) {
            log.error("Chunk build failed to start: {}", e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start chunk build", e)
        }
    }

    @GetMapping("/status/{runId}")
    fun status(
        @PathVariable runId: String,
    ): ResponseEntity<ChunkBuildStatusDto> {
        if (runId.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "runId cannot be blank")
        }
        log.debug("Chunk build status requested: runId={}", runId)
        return try {
            ResponseEntity.ok(orchestrator.status(runId))
        } catch (e: IllegalStateException) {
            log.debug("Run not found: runId={}", runId)
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: $runId", e)
        } catch (e: Exception) {
            log.error("Failed to get chunk build status: runId={}, error={}", runId, e.message, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get status", e)
        }
    }
}
