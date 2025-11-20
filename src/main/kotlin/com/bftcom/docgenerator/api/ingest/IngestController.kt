package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestRunRequest
import com.bftcom.docgenerator.git.api.GitIngestOrchestratorFactory
import com.bftcom.docgenerator.git.model.IngestSummary
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ingest")
class IngestController(
    private val orchestratorFactory: GitIngestOrchestratorFactory,
) {
    @PostMapping("/run")
    fun run(
        @RequestBody @Valid req: IngestRunRequest,
    ): IngestSummary {
        // Определяем провайдера из repoPath (может быть полный URL или путь)
        val repoPath = req.repoPath()
        val repoUrl = if (repoPath.startsWith("http://") || repoPath.startsWith("https://")) {
            repoPath
        } else {
            null // Будет определен позже в orchestrator
        }
        
        val orchestrator = orchestratorFactory.getOrchestrator(repoUrl)
        val summary: IngestSummary =
            orchestrator.runOnce(
                appKey = req.appKey,
                repoPath = repoPath,
                branch = req.branch ?: "develop",
            )
        return summary
    }
}
