package com.bftcom.docgenerator.git.api

import com.bftcom.docgenerator.git.github.GitHubIngestOrchestrator
import com.bftcom.docgenerator.git.gitlab.GitLabIngestOrchestrator
import com.bftcom.docgenerator.git.gitlab.RepoUrlParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GitIngestOrchestratorFactory(
    private val gitLabOrchestrator: GitLabIngestOrchestrator,
    private val gitHubOrchestrator: GitHubIngestOrchestrator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Выбирает оркестратор на основе провайдера из URL
     */
    fun getOrchestrator(repoUrl: String?): GitIngestOrchestrator {
        val parsed = RepoUrlParser.parse(repoUrl)
        return when (parsed.provider?.lowercase()) {
            "github" -> {
                log.debug("Selected GitHub orchestrator for URL: {}", repoUrl)
                gitHubOrchestrator
            }
            "gitlab" -> {
                log.debug("Selected GitLab orchestrator for URL: {}", repoUrl)
                gitLabOrchestrator
            }
            else -> {
                log.warn("Unknown provider '{}' for URL: {}, defaulting to GitLab", parsed.provider, repoUrl)
                gitLabOrchestrator
            }
        }
    }

    /**
     * Выбирает оркестратор на основе провайдера из строки
     */
    fun getOrchestratorByProvider(provider: String?): GitIngestOrchestrator {
        return when (provider?.lowercase()) {
            "github" -> {
                log.debug("Selected GitHub orchestrator for provider: {}", provider)
                gitHubOrchestrator
            }
            "gitlab" -> {
                log.debug("Selected GitLab orchestrator for provider: {}", provider)
                gitLabOrchestrator
            }
            else -> {
                log.warn("Unknown provider '{}', defaulting to GitLab", provider)
                gitLabOrchestrator
            }
        }
    }
}

