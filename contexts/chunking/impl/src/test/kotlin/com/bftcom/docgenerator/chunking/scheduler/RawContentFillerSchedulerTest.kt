package com.bftcom.docgenerator.chunking.scheduler

import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.ai.model.CoderExplainRequest
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
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RawContentFillerSchedulerTest {
    @Test
    fun `pollAndFill - ничего не делает если батч пуст`() {
        val txManager = NoopTxManager()
        val chunkRepo = mockk<ChunkRepository>()
        val coder = mockk<OllamaCoderClient>() // если вызовется — тест упадёт (нет stub)

        every { chunkRepo.lockNextBatchForRawFill(10) } returns emptyList()

        val scheduler = RawContentFillerScheduler(txManager, chunkRepo, coder)
        scheduler.pollAndFill()

        verify(exactly = 1) { chunkRepo.lockNextBatchForRawFill(10) }
    }

    @Test
    fun `pollAndFill - пропускает чанк если codeExcerpt пустой`() {
        val txManager = NoopTxManager()
        val chunkRepo = mockk<ChunkRepository>()
        val coder = mockk<OllamaCoderClient>() // если вызовется — тест упадёт (нет stub)

        val chunk = sampleChunk(id = 1L, sourceCode = "   ")
        every { chunkRepo.lockNextBatchForRawFill(10) } returns listOf(chunk)

        val scheduler = RawContentFillerScheduler(txManager, chunkRepo, coder)
        scheduler.pollAndFill()
    }

    @Test
    fun `pollAndFill - регенерирует ответ если язык не проходит и пишет raw-контент`() {
        val txManager = NoopTxManager()
        val chunkRepo = mockk<ChunkRepository>()
        val coder = mockk<OllamaCoderClient>()

        val chunk = sampleChunk(id = 1L, sourceCode = "fun x() = 1")
        every { chunkRepo.lockNextBatchForRawFill(10) } returns listOf(chunk)

        val req = ExplainRequestFactory.run { chunk.toCoderExplainRequest() }
        val reinforcedReq =
            req.copy(
                hints =
                    (((req.hints ?: "") + "\nТребование: Ответ ДОЛЖЕН быть на РУССКОМ языке. Используй кириллицу.")).trim(),
            )

        every { coder.explain(req) } returns "hello"
        every { coder.explain(reinforcedReq) } returns "привет"

        val updatedAts = mutableListOf<OffsetDateTime>()
        every { chunkRepo.trySetRawContent(id = 1L, content = "привет", updatedAt = capture(updatedAts)) } returns 1

        val scheduler = RawContentFillerScheduler(txManager, chunkRepo, coder)
        setPrivateInt(scheduler, "maxRegens", 1)
        setPrivateDouble(scheduler, "minCyrRatio", 0.9)

        scheduler.pollAndFill()

        verify(exactly = 1) { coder.explain(req) }
        verify(exactly = 1) { coder.explain(reinforcedReq) }
        assertEquals(1, updatedAts.size)
    }

    private fun sampleChunk(
        id: Long,
        sourceCode: String,
    ): Chunk {
        val app = Application(key = "app", name = "App")
        val node = Node(
            id = 10L,
            application = app,
            fqn = "com.example.Foo.bar",
            name = "bar",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            sourceCode = sourceCode,
        )
        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "code",
            content = "content",
            title = node.fqn,
            pipeline = mapOf("params" to mapOf("strategy" to "per-node", "audience" to "coder")),
        )
    }

    private fun setPrivateInt(
        target: Any,
        fieldName: String,
        value: Int,
    ) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.setInt(target, value)
    }

    private fun setPrivateDouble(
        target: Any,
        fieldName: String,
        value: Double,
    ) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.setDouble(target, value)
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

