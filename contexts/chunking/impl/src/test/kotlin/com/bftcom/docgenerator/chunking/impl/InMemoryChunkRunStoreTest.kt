package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.dto.ChunkBuildStatusDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryChunkRunStoreTest {
    private lateinit var store: InMemoryChunkRunStore

    @BeforeEach
    fun setUp() {
        store = InMemoryChunkRunStore()
    }

    @Test
    fun `create - создает новый run с уникальным ID`() {
        // when
        val run1 = store.create(1L, "strategy1")
        val run2 = store.create(2L, "strategy2")

        // then
        assertThat(run1.runId).isNotEqualTo(run2.runId)
        assertThat(run1.startedAt).isNotNull()
        assertThat(run2.startedAt).isNotNull()
    }

    @Test
    fun `create - сохраняет applicationId и strategy`() {
        // when
        val run = store.create(100L, "per-node")
        val status = store.status(run.runId)

        // then
        assertThat(status.applicationId).isEqualTo(100L)
        assertThat(status.state).isEqualTo("created")
    }

    @Test
    fun `markRunning - изменяет состояние на running`() {
        // given
        val run = store.create(1L, "strategy1")

        // when
        store.markRunning(run.runId)
        val status = store.status(run.runId)

        // then
        assertThat(status.state).isEqualTo("running")
    }

    @Test
    fun `markCompleted - устанавливает состояние completed с метриками`() {
        // given
        val run = store.create(1L, "strategy1")

        // when
        store.markCompleted(run.runId, processed = 100, written = 80, skipped = 20)
        val status = store.status(run.runId)

        // then
        assertThat(status.state).isEqualTo("completed")
        assertThat(status.processedNodes).isEqualTo(100)
        assertThat(status.writtenChunks).isEqualTo(80)
        assertThat(status.skippedChunks).isEqualTo(20)
        assertThat(status.finishedAt).isNotNull()
    }

    @Test
    fun `markFailed - устанавливает состояние failed с ошибкой`() {
        // given
        val run = store.create(1L, "strategy1")
        val exception = RuntimeException("Test error")

        // when
        store.markFailed(run.runId, exception)
        val status = store.status(run.runId)

        // then
        assertThat(status.state).isEqualTo("failed")
        assertThat(status.errors).contains("Test error")
        assertThat(status.finishedAt).isNotNull()
    }

    @Test
    fun `markFailed - обрабатывает исключение без сообщения`() {
        // given
        val run = store.create(1L, "strategy1")
        val exception = RuntimeException() // без сообщения

        // when
        store.markFailed(run.runId, exception)
        val status = store.status(run.runId)

        // then
        assertThat(status.state).isEqualTo("failed")
        assertThat(status.errors).contains("RuntimeException")
    }

    @Test
    fun `status - возвращает статус для существующего run`() {
        // given
        val run = store.create(1L, "strategy1")
        store.markRunning(run.runId)

        // when
        val status = store.status(run.runId)

        // then
        assertThat(status.runId).isEqualTo(run.runId)
        assertThat(status.state).isEqualTo("running")
        assertThat(status.startedAt).isEqualTo(run.startedAt)
    }

    @Test
    fun `status - выбрасывает исключение для несуществующего run`() {
        // when & then
        assertThrows(IllegalStateException::class.java) {
            store.status("non-existent-run-id")
        }
    }

    @Test
    fun `markRunning - не падает для несуществующего run`() {
        // when - не должно выбросить исключение
        store.markRunning("non-existent-run-id")
    }

    @Test
    fun `markCompleted - не падает для несуществующего run`() {
        // when - не должно выбросить исключение
        store.markCompleted("non-existent-run-id", 0, 0, 0)
    }

    @Test
    fun `markFailed - не падает для несуществующего run`() {
        // when - не должно выбросить исключение
        store.markFailed("non-existent-run-id", RuntimeException())
    }

    @Test
    fun `lifecycle - полный цикл run`() {
        // given
        val run = store.create(1L, "strategy1")

        // when
        store.markRunning(run.runId)
        var status = store.status(run.runId)
        assertThat(status.state).isEqualTo("running")

        store.markCompleted(run.runId, processed = 50, written = 45, skipped = 5)
        status = store.status(run.runId)

        // then
        assertThat(status.state).isEqualTo("completed")
        assertThat(status.processedNodes).isEqualTo(50)
        assertThat(status.writtenChunks).isEqualTo(45)
        assertThat(status.skippedChunks).isEqualTo(5)
        assertThat(status.errors).isEmpty()
        assertThat(status.finishedAt).isNotNull()
    }

    @Test
    fun `multiple runs - поддерживает несколько параллельных runs`() {
        // given
        val run1 = store.create(1L, "strategy1")
        val run2 = store.create(2L, "strategy2")

        // when
        store.markRunning(run1.runId)
        store.markCompleted(run2.runId, processed = 10, written = 10, skipped = 0)

        // then
        val status1 = store.status(run1.runId)
        val status2 = store.status(run2.runId)

        assertThat(status1.state).isEqualTo("running")
        assertThat(status2.state).isEqualTo("completed")
        assertThat(status1.applicationId).isEqualTo(1L)
        assertThat(status2.applicationId).isEqualTo(2L)
    }
}
