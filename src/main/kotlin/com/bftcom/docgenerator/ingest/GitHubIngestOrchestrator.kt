package com.bftcom.docgenerator.ingest

import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.configprops.GitHubProps
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.git.github.GitHubCheckoutService
import com.bftcom.docgenerator.graph.api.model.BuildResult
import com.bftcom.docgenerator.db.ApplicationRepository
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime

@Service
class GitHubIngestOrchestrator(
    private val git: GitHubCheckoutService,
    private val ghProps: GitHubProps,
    private val appRepo: ApplicationRepository,
    private val graphBuilder: GraphBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 1. Клонирует или обновляет репозиторий GitHub
     * 2. Находит или создаёт Application по ключу (appName)
     * 3. Строит граф с помощью GraphBuilder
     */
    @Transactional
    fun runOnce(): IngestSummary {
        // --- 1) определить appName и checkoutDir ---
        val appName = extractAppName(ghProps.repoUrl)
        val localPath: Path = Path.of(ghProps.basePath, appName)

        // --- 2) клонирование / обновление репозитория ---
        val actualPath =
            git.checkoutOrUpdate(
                repoUrl = ghProps.repoUrl,
                branch = ghProps.branch,
                token = ghProps.token,
                username = ghProps.username,
                password = ghProps.password,
                checkoutDir = localPath,
            )
        log.info("Repo checked out at {}", actualPath)

        // --- 3) извлекаем текущий HEAD SHA ---
        val headSha =
            try {
                Git.open(actualPath.toFile()).use { g -> g.repository.resolve("HEAD")?.name }
            } catch (e: Exception) {
                log.warn("Cannot read HEAD SHA: ${e.message}")
                null
            }

        // --- 4) ensure Application ---
        val parsed = RepoUrlParser.parse(ghProps.repoUrl)
        val app =
            (
                appRepo.findByKey(appName)
                    ?: Application(
                        key = appName,
                        name = parsed.name ?: appName,
                        repoUrl = ghProps.repoUrl,
                        repoProvider = parsed.provider,
                        repoOwner = parsed.owner,
                        repoName = parsed.name,
                        defaultBranch = ghProps.branch,
                    )
            ).apply {
                repoUrl = ghProps.repoUrl
                repoProvider = parsed.provider
                repoOwner = parsed.owner
                repoName = parsed.name
                lastCommitSha = headSha
                lastIndexStatus = "running"
                lastIndexedAt = OffsetDateTime.now()
                updatedAt = OffsetDateTime.now()
            }

        val saved = appRepo.save(app)
        log.info("📇 Using application id={} key={}", saved.id, saved.key)

        // --- 5) сборка графа ---
        val build: BuildResult =
            try {
                graphBuilder.build(saved, actualPath, classpath = emptyList()).also {
                    saved.lastIndexStatus = "success"
                    saved.lastIndexedAt = OffsetDateTime.now()
                    appRepo.save(saved)
                }
            } catch (e: Exception) {
                saved.lastIndexStatus = "failed"
                saved.lastIndexError = e.message
                saved.lastIndexedAt = OffsetDateTime.now()
                appRepo.save(saved)
                throw e
            }

        val took = Duration.between(build.startedAt, build.finishedAt)
        log.info(
            "Build done: nodes={}, edges={}, took={} ms",
            build.nodes,
            build.edges,
            took.toMillis(),
        )

        // --- 6) итог ---
        return IngestSummary(
            appKey = saved.key,
            repoPath = actualPath.toString(),
            headSha = headSha,
            nodes = build.nodes,
            edges = build.edges,
            startedAt = build.startedAt,
            finishedAt = build.finishedAt,
            tookMs = took.toMillis(),
        )
    }

    /** Извлекает имя приложения из URL (например org/repo.git → repo) */
    private fun extractAppName(repoUrl: String): String {
        val name = repoUrl.substringAfterLast('/').removeSuffix(".git")
        return if (name.isBlank()) "unknown-app" else name
    }
}
