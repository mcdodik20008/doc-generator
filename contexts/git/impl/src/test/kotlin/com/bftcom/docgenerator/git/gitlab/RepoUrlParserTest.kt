package com.bftcom.docgenerator.git.gitlab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RepoUrlParserTest {
    @Test
    fun `parse - null и пустые строки`() {
        assertThat(RepoUrlParser.parse(null)).isEqualTo(RepoInfo(null, null, null))
        assertThat(RepoUrlParser.parse("  ")).isEqualTo(RepoInfo(null, null, null))
    }

    @Test
    fun `parse - github url`() {
        val info = RepoUrlParser.parse("https://github.com/acme/my-repo.git")
        assertThat(info.provider).isEqualTo("github")
        assertThat(info.owner).isEqualTo("acme")
        assertThat(info.name).isEqualTo("my-repo")
    }

    @Test
    fun `parse - gitlab url without dotgit`() {
        val info = RepoUrlParser.parse("https://gitlab.example.com/group/sub/repo")
        assertThat(info.provider).isEqualTo("gitlab")
        assertThat(info.owner).isEqualTo("sub")
        assertThat(info.name).isEqualTo("repo")
    }

    @Test
    fun `parse - невалидный url`() {
        val info = RepoUrlParser.parse("not a url")
        assertThat(info).isEqualTo(RepoInfo(null, null, null))
    }
}

