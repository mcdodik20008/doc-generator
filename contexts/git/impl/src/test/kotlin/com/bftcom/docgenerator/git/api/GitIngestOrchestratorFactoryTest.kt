package com.bftcom.docgenerator.git.api

import com.bftcom.docgenerator.git.github.GitHubIngestOrchestrator
import com.bftcom.docgenerator.git.gitlab.GitLabIngestOrchestrator
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitIngestOrchestratorFactoryTest {
    @Test
    fun `getOrchestrator - выбирает по url`() {
        val gl = mockk<GitLabIngestOrchestrator>()
        val gh = mockk<GitHubIngestOrchestrator>()
        val factory = GitIngestOrchestratorFactory(gl, gh)

        assertThat(factory.getOrchestrator("https://github.com/acme/repo.git")).isSameAs(gh)
        assertThat(factory.getOrchestrator("https://gitlab.com/acme/repo.git")).isSameAs(gl)
        assertThat(factory.getOrchestrator("")).isSameAs(gl)
        assertThat(factory.getOrchestrator(null)).isSameAs(gl)
    }

    @Test
    fun `getOrchestratorByProvider - выбирает по provider`() {
        val gl = mockk<GitLabIngestOrchestrator>()
        val gh = mockk<GitHubIngestOrchestrator>()
        val factory = GitIngestOrchestratorFactory(gl, gh)

        assertThat(factory.getOrchestratorByProvider("github")).isSameAs(gh)
        assertThat(factory.getOrchestratorByProvider("GITHUB")).isSameAs(gh)
        assertThat(factory.getOrchestratorByProvider("gitlab")).isSameAs(gl)
        assertThat(factory.getOrchestratorByProvider("unknown")).isSameAs(gl)
        assertThat(factory.getOrchestratorByProvider(null)).isSameAs(gl)
    }
}

