package com.bftcom.docgenerator.api.chunk

import com.bftcom.docgenerator.chunking.api.ChunkBuildOrchestrator
import com.bftcom.docgenerator.chunking.dto.ChunkBuildRequest
import com.bftcom.docgenerator.chunking.dto.ChunkBuildResponse
import com.bftcom.docgenerator.chunking.dto.ChunkBuildStatusDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/chunks")
class ChunkBuildController(
    private val orchestrator: ChunkBuildOrchestrator,
) {
    @PostMapping("/run")
    fun run(
        @RequestBody req: ChunkBuildRequest,
    ): ResponseEntity<ChunkBuildResponse> {
        val run = orchestrator.start(req)
        return ResponseEntity.ok(ChunkBuildResponse(runId = run.runId, startedAt = run.startedAt))
    }

    @GetMapping("/status/{runId}")
    fun status(
        @PathVariable runId: String,
    ): ResponseEntity<ChunkBuildStatusDto> = ResponseEntity.ok(orchestrator.status(runId))
}
