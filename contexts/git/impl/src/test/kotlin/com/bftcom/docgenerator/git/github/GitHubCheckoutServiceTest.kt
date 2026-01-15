package com.bftcom.docgenerator.git.github

import com.bftcom.docgenerator.git.configprops.GitHubProps
import com.bftcom.docgenerator.git.model.GitOperation
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GitHubCheckoutServiceTest {
    @Test
    fun `checkoutOrUpdate - clone локального bare репозитория`(@TempDir dir: Path) {
        val originWork = dir.resolve("origin-work")
        Git.init().setDirectory(originWork.toFile()).call().use { git ->
            Files.writeString(originWork.resolve("README.md"), "hello")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("init").setAuthor("t", "t@t").call()
        }

        val originBare = dir.resolve("origin.git")
        Git.cloneRepository()
            .setURI(originWork.toUri().toString())
            .setDirectory(originBare.toFile())
            .setBare(true)
            .call()
            .use { /* close */ }

        val base = dir.resolve("checkouts")
        val props =
            GitHubProps(
                url = "https://github.com",
                token = "x",
                username = "u",
                password = "p",
                basePath = base.toString(),
            )
        val service = GitHubCheckoutService(props)

        val summary = service.checkoutOrUpdate(repoPath = originBare.toAbsolutePath().toString(), branch = "master", appKey = "app")
        assertThat(summary.operation).isEqualTo(GitOperation.CLONE)
        assertThat(summary.localPath.resolve(".git").toFile()).exists()
    }

    @Test
    fun `resolveRepoUrl - поддерживает полный url и относительный путь`() {
        val props = GitHubProps(url = "https://github.com", basePath = "X")
        val service = GitHubCheckoutService(props)

        assertThat(service.resolveRepoUrl("https://github.com/", "acme/repo"))
            .isEqualTo("https://github.com/acme/repo.git")
        assertThat(service.resolveRepoUrl("https://github.com", "https://x/y.git"))
            .isEqualTo("https://x/y.git")
    }
}

