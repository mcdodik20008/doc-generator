package com.bftcom.docgenerator.api

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ingest")
class IngestController(
    private val orchestrator: IngestOrchestrator
) {
    @PostMapping("/run")
    fun run(): IngestReport = orchestrator.runOnce()
}