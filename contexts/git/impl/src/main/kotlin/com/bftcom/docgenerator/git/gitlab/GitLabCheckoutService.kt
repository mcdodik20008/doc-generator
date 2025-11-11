package com.bftcom.docgenerator.git.gitlab

import com.bftcom.docgenerator.git.configprops.GitLabProps
import com.bftcom.docgenerator.git.model.GitOperation
import com.bftcom.docgenerator.git.model.GitPullSummary
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path
import java.time.OffsetDateTime

@Service
class GitLabCheckoutService(
    private val gitProps: GitLabProps,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun checkoutOrUpdate(
        repoPath: String,
        branch: String,
        appKey: String,
    ): GitPullSummary {
        val checkoutDir: Path = Path.of(gitProps.basePath, appKey)
        val dir = checkoutDir.toFile()
        dir.mkdirs()

        val creds =
            runCatching {
                UsernamePasswordCredentialsProvider(gitProps.username, gitProps.password)
            }.recoverCatching {
                UsernamePasswordCredentialsProvider("oauth2", gitProps.token)
            }.getOrThrow()

        val repoUrl = resolveRepoUrl(gitProps.url, repoPath)
        val now = OffsetDateTime.now()

        // ------------------------------------------------------------------
        // 1) Если репозиторий уже есть — fetch + checkout + pull
        // ------------------------------------------------------------------
        val gitDir = File(dir, ".git")
        if (gitDir.exists()) {
            var before: String? = null
            var after: String? = null
            return try {
                log.info("Repo already exists at {} — pulling...", dir)

                Git.open(dir).use { git ->
                    before = resolveHead(git)
                    git.fetch().setCredentialsProvider(creds).call()
                    git.checkout().setName(branch).call()
                    git.pull().setCredentialsProvider(creds).call()
                    after = resolveHead(git)
                }

                GitPullSummary(
                    repoUrl = repoUrl,
                    branch = branch,
                    appKey = appKey,
                    localPath = checkoutDir,
                    operation = GitOperation.PULL,
                    beforeHead = before,
                    afterHead = after,
                    fetchedAt = now,
                )
            } catch (e: Exception) {
                log.warn("Pull failed: ${e.message}", e)
                val head =
                    runCatching {
                        Git.open(dir).use { resolveHead(it) }
                    }.getOrNull()

                GitPullSummary(
                    repoUrl = repoUrl,
                    branch = branch,
                    appKey = appKey,
                    localPath = checkoutDir,
                    operation = GitOperation.NOOP,
                    beforeHead = head,
                    afterHead = head,
                    fetchedAt = now,
                )
            }
        }

        // ------------------------------------------------------------------
        // 2) Иначе — clone
        // ------------------------------------------------------------------
        log.info("Cloning {} into {} (branch={})", repoUrl, dir, branch)

        try {
            Git
                .cloneRepository()
                .setURI(repoUrl)
                .setBranch(branch)
                .setDirectory(dir)
                .setCredentialsProvider(creds)
                .call()
                .use { /* auto-close */ }
        } catch (e: TransportException) {
            log.error("Git transport error: ${e.message}", e)
            throw e
        }

        val headAfter =
            runCatching {
                Git.open(dir).use { resolveHead(it) }
            }.getOrNull()

        return GitPullSummary(
            repoUrl = repoUrl,
            branch = branch,
            appKey = appKey,
            localPath = checkoutDir,
            operation = GitOperation.CLONE,
            beforeHead = null,
            afterHead = headAfter,
            fetchedAt = now,
        )
    }

    private fun resolveHead(git: Git): String? = git.repository.resolve("HEAD")?.let(ObjectId::name)

    fun resolveRepoUrl(
        baseUrlOrFull: String,
        repoPath: String,
    ): String =
        if (repoPath.startsWith("http://") || repoPath.startsWith("https://") || repoPath.endsWith(".git")) {
            repoPath
        } else {
            baseUrlOrFull.trimEnd('/') + "/" + repoPath.trimStart('/') + ".git"
        }
}
