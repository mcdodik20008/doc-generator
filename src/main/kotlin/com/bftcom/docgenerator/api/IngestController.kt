package com.bftcom.docgenerator.api

import com.bftcom.docgenerator.api.dto.IngestRunRequest
import com.bftcom.docgenerator.ingest.GitLabIngestOrchestrator
import com.bftcom.docgenerator.ingest.IngestSummary
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ingest")
class IngestController(
    private val orchestrator: GitLabIngestOrchestrator,
) {
    @PostMapping("/run")
    fun run(
        @RequestBody @Valid req: IngestRunRequest,
    ): IngestSummary {
        val summary: IngestSummary =
            orchestrator.runOnce(
                appKey = req.appKey,
                repoPath = req.repoPath(),
                branch = req.branch ?: "develop",
            )
        return summary
    }
}
