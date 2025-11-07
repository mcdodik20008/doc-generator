package com.bftcom.docgenerator.git.gitlab

import java.net.URI

object RepoUrlParser {
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
            RepoInfo(provider, owner, nameRaw)
        } catch (_: Exception) {
            RepoInfo(null, null, null)
        }
    }
}
