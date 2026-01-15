package com.bftcom.docgenerator.git.gitlab

import com.bftcom.docgenerator.git.configprops.GitLabProps
import com.bftcom.docgenerator.git.model.GitOperation
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GitLabCheckoutServiceTest {
    @Test
    fun `checkoutOrUpdate - clone затем pull на локальном bare репозитории`(@TempDir dir: Path) {
        val originWork = dir.resolve("origin-work")
        Git.init().setDirectory(originWork.toFile()).call().use { git ->
            Files.writeString(originWork.resolve("README.md"), "hello")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("init").setAuthor("t", "t@t").call()
        }

        // делаем bare-копию с суффиксом .git
        val originBare = dir.resolve("origin.git")
        Git.cloneRepository()
            .setURI(originWork.toUri().toString())
            .setDirectory(originBare.toFile())
            .setBare(true)
            .call()
            .use { /* close */ }

        val base = dir.resolve("checkouts")
        val props =
            GitLabProps(
                url = "https://gitlab.example.com",
                token = "x",
                username = "u",
                password = "p",
                basePath = base.toString(),
            )
        val service = GitLabCheckoutService(props)

        val repoPath = originBare.toAbsolutePath().toString() // endsWith .git

        val first = service.checkoutOrUpdate(repoPath = repoPath, branch = "master", appKey = "app")
        assertThat(first.operation).isEqualTo(GitOperation.CLONE)
        assertThat(first.localPath.toFile()).exists()
        assertThat(first.localPath.resolve(".git").toFile()).exists()

        val second = service.checkoutOrUpdate(repoPath = repoPath, branch = "master", appKey = "app")
        assertThat(second.operation).isEqualTo(GitOperation.PULL)
        assertThat(second.beforeHead).isNotBlank()
        assertThat(second.afterHead).isNotBlank()
    }

    @Test
    fun `resolveRepoUrl - склеивает base и относительный путь`() {
        val props = GitLabProps(url = "https://gitlab.example.com", basePath = "X")
        val service = GitLabCheckoutService(props)

        assertThat(service.resolveRepoUrl("https://gitlab.example.com/", "group/repo"))
            .isEqualTo("https://gitlab.example.com/group/repo.git")
        assertThat(service.resolveRepoUrl("https://gitlab.example.com", "https://x/y.git"))
            .isEqualTo("https://x/y.git")
    }
}

