package com.bftcom.docgenerator.git.gitlab

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.git.api.GitIngestOrchestrator
import com.bftcom.docgenerator.git.model.GitPullSummary
import com.bftcom.docgenerator.git.model.IngestSummary
import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.graph.api.model.BuildResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.OffsetDateTime

@Service
class GitLabIngestOrchestrator(
    private val git: GitLabCheckoutService,
    private val appRepo: ApplicationRepository,
    private val graphBuilder: GraphBuilder,
    private val gradleResolver: GradleClasspathResolver,
    ) : GitIngestOrchestrator {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun runOnce(
        appKey: String,
        repoPath: String,
        branch: String,
    ): IngestSummary {
        val summary: GitPullSummary = git.checkoutOrUpdate(
            repoPath = repoPath,
            branch = branch,
            appKey = appKey
        )
        log.info("✅ Repo checked out at {} (op={}, head={} -> {})",
            summary.localPath, summary.operation, summary.beforeHead, summary.afterHead)

        val localPath = summary.localPath
        val headSha = summary.afterHead

        val parsed: RepoInfo = RepoUrlParser.parse(summary.repoUrl) // <- парсим именно summary.repoUrl
        val app: Application = getOrCreateApp(
            appKey = appKey,
            repoUrl = summary.repoUrl,   // <- передаём отдельным параметром
            parsed = parsed,
            branch = branch,
            headSha = headSha
        )
        val savedApp = appRepo.save(app)
        log.info("📇 Using application id={} key={}", savedApp.id, savedApp.key)

        // --- 4) Выбиваем classpath из gradle-проектов внутри checkout ---
        log.info("Scanning for Gradle projects (gradlew) within [{}]...", localPath)

        val gradleProjectDirs =
            Files
                .walk(localPath)
                .filter { it.fileName.toString() == "gradlew" || it.fileName.toString() == "gradlew.bat" }
                .map { it.parent }
                .distinct()
                .toList()

        val classpath: List<File> =
            if (gradleProjectDirs.isEmpty()) {
                log.warn("No 'gradlew' files found in [{}]. Cannot resolve classpath.", localPath)
                emptyList()
            } else {
                log.info("Found ${gradleProjectDirs.size} Gradle project(s): $gradleProjectDirs")
                gradleProjectDirs
                    .flatMap { projectDir -> gradleResolver.resolveClasspath(projectDir) }
                    .distinct()
            }

        if (classpath.isEmpty()) {
            log.warn("Could not resolve classpath for [${savedApp.key}]. Analysis may be incomplete (PSI bodies may be NULL).")
        } else {
            log.info("Resolved ${classpath.size} TOTAL classpath entries for [${savedApp.key}].")
        }

        // --- 5) build graph ---
        val buildResult: BuildResult =
            try {
                graphBuilder
                    .build(
                        application = savedApp,
                        sourceRoot = localPath,
                        classpath = classpath,
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
        log.info("📦 Build done: nodes={}, edges={}, took={} ms",
            buildResult.nodes, buildResult.edges, took.toMillis())

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

    private fun getOrCreateApp(
        appKey: String,
        repoUrl: String,
        parsed: RepoInfo,
        branch: String,
        headSha: String?
    ): Application = (
            appRepo.findByKey(appKey)
                ?: Application(
                    key = appKey,
                    name = parsed.name ?: appKey,
                    repoUrl = repoUrl,
                    repoProvider = parsed.provider,
                    repoOwner = parsed.owner,
                    repoName = parsed.name,
                    defaultBranch = branch,
                )
            ).apply {
            // актуализируем метаданные всегда
            this.repoUrl = repoUrl
            this.repoProvider = parsed.provider
            this.repoOwner = parsed.owner
            this.repoName = parsed.name
            this.defaultBranch = branch
            this.lastCommitSha = headSha
            this.lastIndexedAt = java.time.OffsetDateTime.now()
            this.lastIndexStatus = "running"
            this.lastIndexError = null
            this.updatedAt = java.time.OffsetDateTime.now()
        }
}