package com.bftcom.docgenerator.git.github

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path

@Service
class GitHubCheckoutService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun checkoutOrUpdate(
        repoUrl: String,
        branch: String,
        username: String,
        password: String,
        token: String,
        checkoutDir: Path
    ): Path {
        val dir = checkoutDir.toFile()
        dir.mkdirs()
        val creds = UsernamePasswordCredentialsProvider(token, "")

        val gitDir = File(dir, ".git")
        if (gitDir.exists()) {
            log.info("Repo already exists at {} — pulling...", dir)
            Git.open(dir).use { git ->
                git.fetch().setCredentialsProvider(creds).call()
                git.checkout().setName(branch).call()
                git.pull().setCredentialsProvider(creds).call()
            }
            return dir.toPath()
        }

        log.info("Cloning {} into {} (branch={})", repoUrl, dir, branch)
        Git.cloneRepository()
            .setURI(repoUrl)
            .setBranch(branch)
            .setDirectory(dir)
            .setCredentialsProvider(creds)
            .call()
            .use { }
        return dir.toPath()
    }
}
