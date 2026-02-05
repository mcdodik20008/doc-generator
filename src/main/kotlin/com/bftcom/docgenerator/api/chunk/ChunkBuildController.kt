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
    // TODO: Отсутствует @Valid для валидации req
    // TODO: Нет обработки ошибок - если orchestrator.start упадет, вернется generic 500
    // TODO: Нет логирования запуска задачи
    fun run(
        @RequestBody req: ChunkBuildRequest,
    ): ResponseEntity<ChunkBuildResponse> {
        // TODO: Нет проверки что задача с таким runId уже не запущена (может быть дубликат)
        val run = orchestrator.start(req)
        return ResponseEntity.ok(ChunkBuildResponse(runId = run.runId, startedAt = run.startedAt))
    }

    @GetMapping("/status/{runId}")
    // TODO: Нет валидации формата runId (может быть пустой строкой или невалидный UUID)
    // TODO: Нет обработки случая когда runId не найден - вернется null или exception?
    // TODO: Отсутствует логирование запросов статуса
    fun status(
        @PathVariable runId: String,
    ): ResponseEntity<ChunkBuildStatusDto> = ResponseEntity.ok(orchestrator.status(runId))
}
