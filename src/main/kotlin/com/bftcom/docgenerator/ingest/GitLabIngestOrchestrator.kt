package com.bftcom.docgenerator.ingest

import com.bftcom.docgenerator.configprops.GitLabProps
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.git.gitlab.GitCheckoutService
import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.graph.model.BuildResult
import com.bftcom.docgenerator.repo.ApplicationRepository
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime

@Service
class GitLabIngestOrchestrator(
    private val git: GitCheckoutService,
    private val gitProps: GitLabProps,
    private val appRepo: ApplicationRepository,
    private val graphBuilder: GraphBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun resolveRepoUrl(
        baseUrlOrFull: String,
        repoPath: String,
    ): String =
        if (repoPath.startsWith("http://") || repoPath.startsWith("https://") || repoPath.endsWith(".git")) {
            repoPath
        } else {
            baseUrlOrFull.trimEnd('/') + "/" + repoPath.trimStart('/') + ".git"
        }

    /**
     * 1) clone/pull GitLab (token ИЛИ username/password)
     * 2) ensure Application (key = repoName)
     * 3) build graph
     * 4) обновить lastCommitSha / lastIndex*
     */
    @Transactional
    fun runOnce(
        appKey: String,
        repoPath: String, // "<group>/<name>"
        branch: String = "develop",
    ): IngestSummary {
        // --- 1) определить appKey и каталог выгрузки ---
        val checkoutDir: Path = Path.of(gitProps.basePath, appKey)

        // --- 2) checkout (clone/pull) ---
        val localPath: Path =
            git.checkoutOrUpdate(
                repoUrl = resolveRepoUrl(gitProps.url, repoPath),
                branch = branch,
                token = gitProps.token,
                username = gitProps.username,
                password = gitProps.password,
                checkoutDir = checkoutDir,
            )
        log.info("✅ Repo checked out at {}", localPath)

        // --- HEAD SHA ---
        val headSha =
            try {
                Git.open(localPath.toFile()).use { it.repository.resolve("HEAD")?.name }
            } catch (e: Exception) {
                log.warn("Cannot resolve HEAD SHA: ${e.message}")
                null
            }

        // --- 3) ensure Application по ключу ---
        val parsed = RepoUrlParser.parse(gitProps.url)
        val app: Application =
            (
                appRepo.findByKey(appKey)
                    ?: Application(
                        key = appKey,
                        name = parsed.name ?: appKey,
                        repoUrl = gitProps.url,
                        repoProvider = parsed.provider,
                        repoOwner = parsed.owner,
                        repoName = parsed.name,
                        defaultBranch = branch,
                    )
            ).apply {
                // держим актуальные метаданные
                repoUrl = gitProps.url
                repoProvider = parsed.provider
                repoOwner = parsed.owner
                repoName = parsed.name
                defaultBranch = branch
                lastCommitSha = headSha
                lastIndexedAt = OffsetDateTime.now()
                lastIndexStatus = "running"
                lastIndexError = null
                updatedAt = OffsetDateTime.now()
            }

        val savedApp = appRepo.save(app)
        log.info("📇 Using application id={} key={}", savedApp.id, savedApp.key)

        // --- 4) build graph ---
        val buildResult: BuildResult =
            try {
                graphBuilder
                    .build(
                        application = savedApp,
                        sourceRoot = localPath,
                    ).also {
                        savedApp.lastIndexStatus = "success"
                        savedApp.lastIndexedAt = OffsetDateTime.now()
                        savedApp.lastIndexError = null
                        appRepo.save(savedApp)
                    }
            } catch (e: Exception) {
                savedApp.lastIndexStatus = "failed"
                savedApp.lastIndexedAt = OffsetDateTime.now()
                savedApp.lastIndexError = (e.message ?: e::class.java.simpleName)
                appRepo.save(savedApp)
                throw e
            }

        val took = Duration.between(buildResult.startedAt, buildResult.finishedAt)
        log.info(
            "📦 Build done: nodes={}, edges={}, chunks={}, took={} ms",
            buildResult.nodes,
            buildResult.edges,
            took.toMillis(),
        )

        return IngestSummary(
            appKey = savedApp.key,
            repoPath = localPath.toString(),
            headSha = headSha,
            nodes = buildResult.nodes,
            edges = buildResult.edges,
            startedAt = buildResult.startedAt,
            finishedAt = buildResult.finishedAt,
            tookMs = took.toMillis(),
        )
    }

    /** org/repo(.git) → repo */
    private fun extractRepoName(repoUrl: String): String = repoUrl.substringAfterLast('/').removeSuffix(".git").ifBlank { "unknown-app" }
}
