package com.bftcom.docgenerator.chunking.scheduler

import com.bftcom.docgenerator.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.chunking.factory.ExplainRequestFactory
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentFillerSchedulerTest {
    @Test
    fun `pollAndFill - не вызывает модель если батч пуст`() {
        val txManager = NoopTxManager()
        val chunkRepo = mockk<ChunkRepository>()
        val talker = mockk<OllamaTalkerClient>() // если вызовется — тест упадёт (нет stub)

        every { chunkRepo.lockNextBatchContentForFill(10) } returns emptyList()

        val scheduler = ContentFillerScheduler(txManager, chunkRepo, talker)
        scheduler.pollAndFill()

        verify(exactly = 1) { chunkRepo.lockNextBatchContentForFill(10) }
    }

    @Test
    fun `pollAndFill - заполняет content через talker и сохраняет`() {
        val txManager = NoopTxManager()
        val chunkRepo = mockk<ChunkRepository>()
        val talker = mockk<OllamaTalkerClient>()

        val chunk = sampleChunk(id = 1L, contentRaw = "сырой текст")
        val reloaded = sampleChunk(id = 1L, contentRaw = "сырой текст")

        val req = ExplainRequestFactory.run { chunk.toTalkerRewriteRequest() }

        every { chunkRepo.lockNextBatchContentForFill(10) } returns listOf(chunk)
        every { talker.rewrite(req) } returns "готовый текст"
        every { chunkRepo.findById(1L) } returns Optional.of(reloaded)
        every { chunkRepo.save(any()) } answers { firstArg() }

        val scheduler = ContentFillerScheduler(txManager, chunkRepo, talker)
        scheduler.pollAndFill()

        assertEquals("готовый текст", reloaded.content)
        verify(exactly = 1) { talker.rewrite(req) }
        verify(exactly = 1) { chunkRepo.save(reloaded) }
    }

    private fun sampleChunk(
        id: Long,
        contentRaw: String,
    ): Chunk {
        val app = Application(key = "app", name = "App")
        val node = Node(
            id = 10L,
            application = app,
            fqn = "com.example.Foo",
            name = "Foo",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            sourceCode = "class Foo {}",
        )
        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "doc",
            contentRaw = contentRaw,
            content = "null",
            title = node.fqn,
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

