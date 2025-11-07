package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.git.gitlab.GitLabIngestOrchestrator
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.system.measureTimeMillis

@Component
class IngestOnStartRunner(
    private val orchestrator: GitLabIngestOrchestrator,
    private val appRepo: ApplicationRepository,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        val argList = args.filterNotNull()

        if (argList.none { it.startsWith("--ingest") }) {
            log.info("⏭️  IngestOnStartRunner: no ingest flags passed, skipping")
            return
        }

        val specificApp =
            argList
                .firstOrNull { it.startsWith("--ingest-app=") }
                ?.substringAfter("=")

        val apps =
            when {
                specificApp != null -> {
                    val app =
                        appRepo.findByKey(specificApp)
                            ?: run {
                                log.error("Application with key={} not found", specificApp)
                                return
                            }
                    listOf(app)
                }
                else -> appRepo.findAll()
            }

        if (apps.isEmpty()) {
            log.warn("No applications found to ingest")
            return
        }

        log.info("Starting ingest for ${apps.size} application(s): {}", apps.map { it.key })
        apps.forEach { app ->
            try {
                val took =
                    measureTimeMillis {
                        val summary =
                            orchestrator.runOnce(
                                appKey = app.key,
                                repoPath = app.repoUrl ?: "",
                                branch = app.defaultBranch,
                            )
                        log.info(
                            "Ingest completed for {}: nodes={}, edges={}",
                            app.key,
                            summary.nodes,
                            summary.edges,
                        )
                    }
                log.info("Ingest for {} took {} ms", app.key, took)
            } catch (ex: Exception) {
                log.error("Ingest failed for {}: {}", app.key, ex.message, ex)
            }
        }

        log.info("IngestOnStartRunner finished all tasks.")
    }
}
