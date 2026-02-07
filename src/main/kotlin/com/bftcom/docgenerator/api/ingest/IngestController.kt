package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestRunRequest
import com.bftcom.docgenerator.git.api.GitIngestOrchestratorFactory
import com.bftcom.docgenerator.git.model.IngestSummary
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/internal/ingest")
class IngestController(
    private val orchestratorFactory: GitIngestOrchestratorFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val INGEST_TIMEOUT_SECONDS = 600L
    }

    @PostMapping("/run")
    // NOTE: Consider full async processing (@Async + task ID tracking) for very large repos
    fun run(
        @RequestBody @Valid req: IngestRunRequest,
    ): IngestSummary {
        // Audit logging для критичной операции
        val repoPath = req.repoPath()
        log.info("Ingest operation started: appKey=${req.appKey}, repoPath=$repoPath, branch=${req.branch ?: "develop"}")
        val repoUrl = if (repoPath.startsWith("http://") || repoPath.startsWith("https://")) {
            repoPath
        } else {
            null // Будет определен позже в orchestrator
        }

        val orchestrator = orchestratorFactory.getOrchestrator(repoUrl)

        return try {
            val summary: IngestSummary = CompletableFuture.supplyAsync {
                orchestrator.runOnce(
                    appKey = req.appKey,
                    repoPath = repoPath,
                    branch = req.branch ?: "develop",
                )
            }.orTimeout(INGEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).get()
            log.info("Ingest operation completed successfully: appKey=${req.appKey}, nodesProcessed=${summary.nodes}, edgesCreated=${summary.edges}")
            summary
        } catch (e: TimeoutException) {
            log.error("Ingest operation timed out after ${INGEST_TIMEOUT_SECONDS}s: appKey=${req.appKey}, repoPath=$repoPath")
            throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Ingest operation timed out after ${INGEST_TIMEOUT_SECONDS} seconds")
        } catch (e: Exception) {
            log.error("Ingest operation failed: appKey=${req.appKey}, repoPath=$repoPath, error=${e.message}", e)
            throw e
        }
    }
}
