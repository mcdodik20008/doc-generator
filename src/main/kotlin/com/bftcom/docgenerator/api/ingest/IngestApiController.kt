package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.git.api.GitIngestOrchestratorFactory
import com.bftcom.docgenerator.git.model.IngestSummary
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/api/ingest")
class IngestApiController(
    private val orchestratorFactory: GitIngestOrchestratorFactory,
    private val applicationRepository: ApplicationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val INGEST_TIMEOUT_SECONDS = 600L
    }

    @PostMapping("/reindex/{appId}")
    fun reindex(@PathVariable appId: Long): IngestSummary {
        val app = applicationRepository.findById(appId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found: $appId")
        }

        val repoOwner = app.repoOwner
        val repoName = app.repoName
        if (repoOwner.isNullOrBlank() || repoName.isNullOrBlank()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Application $appId has no repository info (repoOwner/repoName)",
            )
        }

        val repoPath = "$repoOwner/$repoName"
        val branch = app.defaultBranch
        val appKey = app.key
        log.info("Reindex triggered for appId=$appId, appKey=$appKey, repoPath=$repoPath, branch=$branch")

        val orchestrator = orchestratorFactory.getOrchestratorByProvider(app.repoProvider)

        return try {
            val summary = CompletableFuture.supplyAsync {
                orchestrator.runOnce(appKey = appKey, repoPath = repoPath, branch = branch)
            }.orTimeout(INGEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).get()
            log.info("Reindex completed: appKey=$appKey, nodes=${summary.nodes}, edges=${summary.edges}")
            summary
        } catch (e: TimeoutException) {
            log.error("Reindex timed out after ${INGEST_TIMEOUT_SECONDS}s: appKey=$appKey")
            throw ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "Reindex timed out after ${INGEST_TIMEOUT_SECONDS} seconds",
            )
        } catch (e: Exception) {
            log.error("Reindex failed: appKey=$appKey, error=${e.message}", e)
            throw e
        }
    }
}
