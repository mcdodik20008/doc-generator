package com.bftcom.docgenerator.git.gitlab

import org.slf4j.LoggerFactory
import java.net.URI

object RepoUrlParser {
    private val log = LoggerFactory.getLogger(javaClass)

    fun parse(repoUrl: String?): RepoInfo {
        if (repoUrl.isNullOrBlank()) return RepoInfo(null, null, null)
        return try {
            val uri = URI(repoUrl)
            val host = (uri.host ?: "").lowercase()
            val provider =
                when {
                    "gitlab" in host -> "gitlab"
                    "github" in host -> "github"
                    "bitbucket" in host -> "bitbucket"
                    "gitea" in host -> "gitea"
                    else -> "other"
                }
            // path like: /group/subgroup/repo.git
            val parts = uri.path.trim('/').split('/')
            val nameRaw = parts.lastOrNull()?.removeSuffix(".git")
            val owner = if (parts.size >= 2) parts[parts.size - 2] else null
            val info = RepoInfo(provider, owner, nameRaw)
            log.debug("Parsed repo URL: url={}, provider={}, owner={}, name={}", repoUrl, provider, owner, nameRaw)
            info
        } catch (e: Exception) {
            log.warn("Failed to parse repo URL: url={}, error={}", repoUrl, e.message)
            RepoInfo(null, null, null)
        }
    }
}
