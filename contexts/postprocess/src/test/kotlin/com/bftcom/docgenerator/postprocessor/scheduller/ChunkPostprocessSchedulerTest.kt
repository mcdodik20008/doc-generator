package com.bftcom.docgenerator.postprocessor.scheduller

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.postprocessor.handler.HandlerChainOrchestrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus

class ChunkPostprocessSchedulerTest {
    @Test
    fun `lockBatch - вызывает repo с правильными параметрами`() {
        val tx = NoopTxManager()
        val repo = mockk<ChunkRepository>()
        val orch = mockk<HandlerChainOrchestrator>(relaxed = true)

        every { repo.lockNextBatchForPostprocess(limit = 2, withEmb = true) } returns emptyList()

        val scheduler = ChunkPostprocessScheduler(tx, repo, orch, batchSize = 2, embedEnabled = true)
        scheduler.lockBatch()

        verify(exactly = 1) { repo.lockNextBatchForPostprocess(2, true) }
    }

    @Test
    fun `poll - пустой батч ничего не делает`() {
        val tx = NoopTxManager()
        val repo = mockk<ChunkRepository>()
        val orch = mockk<HandlerChainOrchestrator>(relaxed = true)

        every { repo.lockNextBatchForPostprocess(limit = 1, withEmb = false) } returns emptyList()

        val scheduler = ChunkPostprocessScheduler(tx, repo, orch, batchSize = 1, embedEnabled = false)
        scheduler.poll()

        verify(exactly = 0) { orch.processOne(any()) }
    }

    @Test
    fun `poll - прогоняет обработку и глотает исключения`() {
        val tx = NoopTxManager()
        val repo = mockk<ChunkRepository>()
        val orch = mockk<HandlerChainOrchestrator>()

        val c1 = sampleChunk(1L)
        val c2 = sampleChunk(2L)

        every { repo.lockNextBatchForPostprocess(limit = 2, withEmb = true) } returns listOf(c1, c2)
        every { orch.processOne(c1) } throws RuntimeException("boom")
        every { orch.processOne(c2) } returns Unit

        val scheduler = ChunkPostprocessScheduler(tx, repo, orch, batchSize = 2, embedEnabled = true)
        scheduler.poll()

        verify(exactly = 1) { orch.processOne(c1) }
        verify(exactly = 1) { orch.processOne(c2) }
    }

    private fun sampleChunk(id: Long): Chunk {
        val app = Application(key = "app", name = "App")
        val node =
            Node(
                id = 10L,
                application = app,
                fqn = "com.example.Foo",
                name = "Foo",
                kind = NodeKind.CLASS,
                lang = Lang.kotlin,
            )
        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "code",
            content = "content",
            title = "t",
        )
    }

    private class NoopTxManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = NoopStatus

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit

        private object NoopStatus : TransactionStatus {
            private var rollbackOnly: Boolean = false

            override fun isNewTransaction(): Boolean = true

            override fun hasSavepoint(): Boolean = false

            override fun setRollbackOnly() {
                rollbackOnly = true
            }

            override fun isRollbackOnly(): Boolean = rollbackOnly

            override fun flush() = Unit

            override fun isCompleted(): Boolean = false

            override fun createSavepoint(): Any = throw UnsupportedOperationException("savepoints are not supported")

            override fun rollbackToSavepoint(savepoint: Any) =
                throw UnsupportedOperationException("savepoints are not supported")

            override fun releaseSavepoint(savepoint: Any) =
                throw UnsupportedOperationException("savepoints are not supported")
        }
    }
}

