package com.bftcom.docgenerator.git.gitlab

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path

@Service
class GitCheckoutService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun checkoutOrUpdate(
        repoUrl: String,
        branch: String,
        token: String,
        username: String,
        password: String,
        checkoutDir: Path,
    ): Path {
        val dir = checkoutDir.toFile()
        dir.mkdirs()

        val creds =
            runCatching {
                UsernamePasswordCredentialsProvider(username, password)
            }.recoverCatching {
                UsernamePasswordCredentialsProvider("oauth2", token)
            }.getOrThrow()

        // если уже клонировано — делаем pull
        val gitDir = File(dir, ".git")
        if (gitDir.exists()) {
            return try {
                log.info("Repo already exists at {} — pulling...", dir)
                Git.open(dir).use { git ->
                    git.fetch().setCredentialsProvider(creds).call()
                    git.checkout().setName(branch).call()
                    git.pull().setCredentialsProvider(creds).call()
                }
                dir.toPath()
            } catch (_: Exception) {
                dir.toPath()
            }
        }

        // иначе clone
        log.info("Cloning {} into {} (branch={})", repoUrl, dir, branch)
        try {
            Git
                .cloneRepository()
                .setURI(repoUrl)
                .setBranch(branch)
                .setDirectory(dir)
                .setCredentialsProvider(creds)
                .call()
                .use { }
        } catch (e: TransportException) {
            log.error("Git transport error: ${e.message}", e)
            throw e
        }
        return dir.toPath()
    }
}
