package com.bftcom.docgenerator.ingest

import com.bftcom.docgenerator.configprops.DocgenIngestProps
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
class IngestOrchestrator(
    private val git: GitCheckoutService,
    private val gitProps: GitLabProps,
    private val ingestProps: DocgenIngestProps,
    private val appRepo: ApplicationRepository,
    private val graphBuilder: GraphBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 1) clone/pull Git
     * 2) ensure Application
     * 3) build graph
     * 4) обновить lastCommitSha / lastIndex* поля
     */
    @Transactional
    fun runOnce(): IngestSummary {
        // 1) checkout (clone/pull)
        val localPath: Path = git.checkoutOrUpdate(
            repoUrl = gitProps.repoUrl,
            branch = gitProps.branch,
            username = gitProps.username,
            password = gitProps.password,
            checkoutDir = Path.of(ingestProps.checkoutDir)
        )
        log.info("✅ Repo checked out at {}", localPath)

        // получим текущий HEAD sha
        val headSha = try {
            Git.open(localPath.toFile()).use { g ->
                g.repository.resolve("HEAD")?.name
            }
        } catch (e: Exception) {
            log.warn("Cannot resolve HEAD SHA: ${e.message}")
            null
        }

        // 2) ensure Application по ключу
        val existing = appRepo.findByKey(ingestProps.appKey)
        val parsed = RepoUrlParser.parse(gitProps.repoUrl)

        val app: Application = (existing ?: Application(
            key = ingestProps.appKey,
            name = parsed.name ?: ingestProps.appKey,      // обязательное поле
            description = null,
            repoUrl = gitProps.repoUrl,
            repoProvider = parsed.provider,
            repoOwner = parsed.owner,
            repoName = parsed.name,
            monorepoPath = null,
            defaultBranch = gitProps.branch,
        )).apply {
            // при каждом запуске держим метаданные в актуальном состоянии
            repoUrl = gitProps.repoUrl
            repoProvider = parsed.provider
            repoOwner = parsed.owner
            repoName = parsed.name
            defaultBranch = gitProps.branch
            lastCommitSha = headSha
            lastIndexedAt = OffsetDateTime.now()
            lastIndexStatus = "running"
            lastIndexError = null
            updatedAt = OffsetDateTime.now()
        }

        val savedApp = appRepo.save(app)
        log.info("📇 Using application id={} key={}", savedApp.id, savedApp.key)

        // 3) build graph
        val buildResult: BuildResult = try {
            val r = graphBuilder.build(
                application = savedApp,
                sourceRoot = localPath
            )
            // success
            savedApp.lastIndexStatus = "success"
            savedApp.lastIndexedAt = OffsetDateTime.now()
            savedApp.lastIndexError = null
            appRepo.save(savedApp)
            r
        } catch (e: Exception) {
            // failure
            savedApp.lastIndexStatus = "failed"
            savedApp.lastIndexedAt = OffsetDateTime.now()
            savedApp.lastIndexError = (e.message ?: e::class.java.simpleName)
            appRepo.save(savedApp)
            throw e
        }

        val took = Duration.between(buildResult.startedAt, buildResult.finishedAt)
        log.info(
            "📦 Build done: nodes={}, edges={}, chunks={}, took={} ms",
            buildResult.nodes, buildResult.edges, buildResult.chunks, took.toMillis()
        )

        return IngestSummary(
            appKey = savedApp.key,
            repoPath = localPath.toString(),
            headSha = headSha,
            nodes = buildResult.nodes,
            edges = buildResult.edges,
            chunks = buildResult.chunks,
            startedAt = buildResult.startedAt,
            finishedAt = buildResult.finishedAt,
            tookMs = took.toMillis()
        )
    }
}
