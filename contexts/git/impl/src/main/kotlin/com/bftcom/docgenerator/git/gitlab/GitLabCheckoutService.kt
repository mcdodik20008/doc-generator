package com.bftcom.docgenerator.git.gitlab

import com.bftcom.docgenerator.git.configprops.GitLabProps
import com.bftcom.docgenerator.git.model.GitOperation
import com.bftcom.docgenerator.git.model.GitPullSummary
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class GitLabCheckoutService(
    private val gitProps: GitLabProps,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Блокировки для предотвращения конкурентного доступа к одной директории
    private val locks = ConcurrentHashMap<String, Any>()

    fun checkoutOrUpdate(
        repoPath: String,
        branch: String,
        appKey: String,
    ): GitPullSummary = synchronized(locks.computeIfAbsent(appKey) { Any() }) {
        val checkoutDir: Path = Path.of(gitProps.basePath, appKey)
        val dir = checkoutDir.toFile()
        val repoUrl = resolveRepoUrl(gitProps.url, repoPath)
        val creds = resolveCredentials()
        val now = OffsetDateTime.now()

        return if (isValidRepository(dir)) {
            updateExisting(dir, branch, repoUrl, appKey, checkoutDir, creds, now)
        } else {
            cloneNew(dir, branch, repoUrl, appKey, checkoutDir, creds, now)
        }
    }

    private fun updateExisting(
        dir: File,
        branch: String,
        repoUrl: String,
        appKey: String,
        checkoutDir: Path,
        creds: CredentialsProvider,
        now: OffsetDateTime
    ): GitPullSummary {
        return try {
            log.info("Updating existing repo at {} (branch={})", dir, branch)
            Git.open(dir).use { git ->
                val before = resolveHead(git)

                // 1. Fetch актуальных данных
                git.fetch()
                    .setCredentialsProvider(creds)
                    .setRemote("origin")
                    .setCheckFetchedObjects(true)
                    .call()

                // 2. Жесткий сброс к состоянию удаленной ветки
                // Это удаляет локальные изменения и решает проблемы с merge
                git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef("origin/$branch")
                    .call()

                val after = resolveHead(git)

                GitPullSummary(
                    repoUrl = repoUrl,
                    branch = branch,
                    appKey = appKey,
                    localPath = checkoutDir,
                    operation = GitOperation.PULL,
                    beforeHead = before,
                    afterHead = after,
                    fetchedAt = now
                )
            }
        } catch (e: Exception) {
            log.warn("Update failed for {}, attempting re-clone. Error: {}", appKey, e.message)
            dir.deleteRecursively()
            cloneNew(dir, branch, repoUrl, appKey, checkoutDir, creds, now)
        }
    }

    private fun cloneNew(
        dir: File,
        branch: String,
        repoUrl: String,
        appKey: String,
        checkoutDir: Path,
        creds: CredentialsProvider,
        now: OffsetDateTime
    ): GitPullSummary {
        log.info("Cloning {} into {} (branch={})", repoUrl, dir, branch)
        dir.mkdirs()

        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(dir)
            .setBranch(branch)
            .setCredentialsProvider(creds)
            .setCloneAllBranches(false)
            .call()
            .use { git ->
                return GitPullSummary(
                    repoUrl = repoUrl,
                    branch = branch,
                    appKey = appKey,
                    localPath = checkoutDir,
                    operation = GitOperation.CLONE,
                    beforeHead = null,
                    afterHead = resolveHead(git),
                    fetchedAt = now
                )
            }
    }

    private fun resolveCredentials(): CredentialsProvider {
        return when {
            gitProps.token.isNotBlank() ->
                UsernamePasswordCredentialsProvider("oauth2", gitProps.token)
            gitProps.username.isNotBlank() ->
                UsernamePasswordCredentialsProvider(gitProps.username, gitProps.password ?: "")
            else -> CredentialsProvider.getDefault()
        }
    }

    private fun isValidRepository(dir: File): Boolean {
        return File(dir, ".git").exists() && try {
            Git.open(dir).use { it.repository.objectDatabase.exists() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveHead(git: Git): String? =
        git.repository.resolve("HEAD")?.name

    fun resolveRepoUrl(baseUrlOrFull: String, repoPath: String): String =
        if (repoPath.startsWith("http") || repoPath.endsWith(".git")) {
            repoPath
        } else {
            "${baseUrlOrFull.trimEnd('/')}/${repoPath.trimStart('/')}.git"
        }
}