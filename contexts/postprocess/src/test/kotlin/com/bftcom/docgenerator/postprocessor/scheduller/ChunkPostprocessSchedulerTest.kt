package com.bftcom.docgenerator.postprocessor.scheduller

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.postprocessor.handler.HandlerChainOrchestrator
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkPostprocessSchedulerTest {
    private lateinit var repository: ChunkRepository
    private lateinit var orchestrator: HandlerChainOrchestrator
    private lateinit var scheduler: ChunkPostprocessScheduler

    @BeforeEach
    fun setUp() {
        repository = mockk()
        orchestrator = mockk()
        scheduler = ChunkPostprocessScheduler(
            txManager = mockk(relaxed = true),
            repo = repository,
            orchestrator = orchestrator,
            batchSize = 2,
            embedEnabled = true,
        )
    }

    @Test
    fun `lockBatch delegates to repository with configured params`() {
        every { repository.lockNextBatchForPostprocess(limit = 2, withEmb = true) } returns emptyList()

        val batch = scheduler.lockBatch()

        assertThat(batch).isEmpty()
        verify(exactly = 1) { repository.lockNextBatchForPostprocess(limit = 2, withEmb = true) }
    }

    @Test
    fun `poll does nothing when batch is empty`() {
        every { repository.lockNextBatchForPostprocess(limit = 2, withEmb = true) } returns emptyList()

        scheduler.poll()

        verify(exactly = 0) { orchestrator.processOne(any()) }
    }

    @Test
    fun `poll processes batch and continues on handler errors`() {
        val first = createChunk(id = 1L)
        val second = createChunk(id = 2L)
        every { repository.lockNextBatchForPostprocess(limit = 2, withEmb = true) } returns listOf(first, second)
        every { orchestrator.processOne(first) } throws RuntimeException("boom")
        every { orchestrator.processOne(second) } just runs

        scheduler.poll()

        verify(exactly = 1) { orchestrator.processOne(first) }
        verify(exactly = 1) { orchestrator.processOne(second) }
    }

    private fun createChunk(id: Long): Chunk {
        val app = Application(key = "app", name = "App")
        app.id = 1L
        val node = Node(
            application = app,
            fqn = "com.example.Test",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            packageName = "com.example",
        )
        node.id = 10L

        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "doc",
            content = "content",
        )
    }
}
