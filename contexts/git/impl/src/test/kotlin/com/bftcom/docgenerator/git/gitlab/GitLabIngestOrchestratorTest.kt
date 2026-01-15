package com.bftcom.docgenerator.git.gitlab

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.git.model.GitOperation
import com.bftcom.docgenerator.git.model.GitPullSummary
import com.bftcom.docgenerator.graph.api.events.LibraryBuildRequestedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

class GitLabIngestOrchestratorTest {
    @Test
    fun `runOnce - создает app, резолвит classpath и публикует событие`(@TempDir dir: Path) {
        val localPath = dir.resolve("checkout").also { Files.createDirectories(it) }
        // имитируем gradle проект (достаточно файла gradlew.bat)
        Files.writeString(localPath.resolve("gradlew.bat"), "@echo off\r\necho ok\r\n")

        val checkout = mockk<GitLabCheckoutService>()
        every {
            checkout.checkoutOrUpdate(
                repoPath = "https://gitlab.example.com/acme/repo.git",
                branch = "master",
                appKey = "app",
            )
        } returns
            GitPullSummary(
                repoUrl = "https://gitlab.example.com/acme/repo.git",
                branch = "master",
                appKey = "app",
                localPath = localPath,
                operation = GitOperation.CLONE,
                beforeHead = null,
                afterHead = "sha",
                fetchedAt = OffsetDateTime.now(),
            )

        val appRepo = mockk<ApplicationRepository>()
        every { appRepo.findByKey("app") } returns null
        every { appRepo.save(any()) } answers {
            val a = firstArg<Application>()
            if (a.id == null) a.id = 1L
            a
        }

        val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

        val resolver = mockk<GradleClasspathResolver>()
        every { resolver.resolveClasspath(any()) } returns listOf(File("x.jar"))

        val orch = GitLabIngestOrchestrator(checkout, appRepo, publisher, resolver)
        val res = orch.runOnce(appKey = "app", repoPath = "https://gitlab.example.com/acme/repo.git", branch = "master")

        assertThat(res.appKey).isEqualTo("app")
        assertThat(res.headSha).isEqualTo("sha")

        verify(atLeast = 1) { publisher.publishEvent(any<LibraryBuildRequestedEvent>()) }
        verify(atLeast = 2) { appRepo.save(any()) }
    }
}

