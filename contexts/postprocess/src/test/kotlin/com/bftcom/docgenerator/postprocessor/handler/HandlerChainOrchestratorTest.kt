package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime

class HandlerChainOrchestratorTest {

    private lateinit var repository: ChunkRepository
    private lateinit var handler1: PostprocessHandler
    private lateinit var handler2: PostprocessHandler
    private lateinit var orchestrator: HandlerChainOrchestrator

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        handler1 = mockk(relaxed = true)
        handler2 = mockk(relaxed = true)
        orchestrator = HandlerChainOrchestrator(repository, listOf(handler1, handler2))
    }

    @Test
    fun `processOne - вызывает handlers которые поддерживают snapshot`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
            contentHash = null,
            tokenCount = null,
        )

        val mutation1 = PartialMutation().set(FieldKey.CONTENT_HASH, "hash1")
        val mutation2 = PartialMutation().set(FieldKey.TOKEN_COUNT, 100)

        every { handler1.supports(any()) } returns true
        every { handler2.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation1
        every { handler2.produce(any()) } returns mutation2
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) { handler1.supports(any()) }
        verify(exactly = 1) { handler2.supports(any()) }
        verify(exactly = 1) { handler1.produce(any()) }
        verify(exactly = 1) { handler2.produce(any()) }
    }

    @Test
    fun `processOne - пропускает handlers которые не поддерживают snapshot`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation().set(FieldKey.CONTENT_HASH, "hash1")

        every { handler1.supports(any()) } returns true
        every { handler2.supports(any()) } returns false
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) { handler1.supports(any()) }
        verify(exactly = 1) { handler2.supports(any()) }
        verify(exactly = 1) { handler1.produce(any()) }
        verify(exactly = 0) { handler2.produce(any()) }
    }

    @Test
    fun `processOne - обрабатывает ошибки в handlers и продолжает работу`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation().set(FieldKey.CONTENT_HASH, "hash1")

        every { handler1.supports(any()) } returns true
        every { handler2.supports(any()) } returns true
        every { handler1.produce(any()) } throws RuntimeException("Handler failed")
        every { handler2.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) { handler1.produce(any()) }
        verify(exactly = 1) { handler2.produce(any()) }
        verify(exactly = 1) { repository.updateMeta(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processOne - использует существующие значения из chunk для initial mutation`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
            contentHash = "existing_hash",
            tokenCount = 50,
            embedModel = "existing_model",
            embedTs = OffsetDateTime.parse("2024-01-01T00:00:00Z"),
            emb = floatArrayOf(1.0f, 2.0f),
        )

        every { handler1.supports(any()) } returns false
        every { handler2.supports(any()) } returns false
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        // Проверяем, что использованы существующие значения
        verify(exactly = 1) {
            repository.updateMeta(
                id = 1L,
                contentHash = "existing_hash",
                tokenCount = 50,
                embedModel = null, // Обнуляется при первой записи
                embedTs = null, // Обнуляется при первой записи
            )
        }
        verify(exactly = 1) {
            repository.updateEmb(
                id = 1L,
                embLiteral = "[1.0,2.0]",
            )
        }
        verify(exactly = 1) {
            repository.updateMeta(
                id = 1L,
                contentHash = "existing_hash",
                tokenCount = 50,
                embedModel = "existing_model",
                embedTs = OffsetDateTime.parse("2024-01-01T00:00:00Z"),
            )
        }
    }

    @Test
    fun `processOne - вычисляет contentHash если его нет`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
            contentHash = null,
            tokenCount = null,
        )

        every { handler1.supports(any()) } returns false
        every { handler2.supports(any()) } returns false
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        // Проверяем, что contentHash был вычислен (SHA-256 от "test content")
        verify(exactly = 1) {
            repository.updateMeta(
                id = 1L,
                contentHash = any(), // Вычисленный хэш
                tokenCount = 2, // "test content" = 2 токена
                embedModel = null,
                embedTs = null,
            )
        }
    }

    @Test
    fun `processOne - вычисляет tokenCount если его нет`() {
        val chunk = createChunk(
            id = 1L,
            content = "hello world test",
            contentHash = "existing_hash",
            tokenCount = null,
        )

        every { handler1.supports(any()) } returns false
        every { handler2.supports(any()) } returns false
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) {
            repository.updateMeta(
                id = 1L,
                contentHash = "existing_hash",
                tokenCount = 3, // "hello world test" = 3 токена
                embedModel = null,
                embedTs = null,
            )
        }
    }

    @Test
    fun `processOne - обновляет embedding если он присутствует в merged mutation`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(1.0f, 2.0f, 3.0f))
            .set(FieldKey.EMBED_MODEL, "new_model")
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-02T00:00:00Z"))

        every { handler1.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1
        every { repository.updateEmb(any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) {
            repository.updateEmb(
                id = 1L,
                embLiteral = "[1.0,2.0,3.0]",
            )
        }
        verify(exactly = 2) { repository.updateMeta(any(), any(), any(), any(), any()) }
        verify {
            repository.updateMeta(
                id = 1L,
                contentHash = any(),
                tokenCount = any(),
                embedModel = "new_model",
                embedTs = OffsetDateTime.parse("2024-01-02T00:00:00Z"),
            )
        }
    }

    @Test
    fun `processOne - использует текущее время для embedTs если оно не указано`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(1.0f, 2.0f))

        every { handler1.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1
        every { repository.updateEmb(any(), any()) } returns 1

        val beforeTime = OffsetDateTime.now()
        orchestrator.processOne(chunk)
        val afterTime = OffsetDateTime.now().plusSeconds(1)

        verify {
            repository.updateMeta(
                id = 1L,
                contentHash = any(),
                tokenCount = any(),
                embedModel = null,
                embedTs = match {
                    it is OffsetDateTime && (it.isAfter(beforeTime) || it.isEqual(beforeTime)) && 
                    (it.isBefore(afterTime) || it.isEqual(afterTime))
                },
            )
        }
    }

    @Test
    fun `processOne - выбрасывает исключение при ошибке updateMeta`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        every { handler1.supports(any()) } returns false
        every { repository.updateMeta(any(), any(), any(), any(), any()) } throws RuntimeException("DB error")

        assertThrows<RuntimeException> {
            orchestrator.processOne(chunk)
        }

        verify(exactly = 1) { repository.updateMeta(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processOne - выбрасывает исключение при ошибке updateEmb`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(1.0f, 2.0f))

        every { handler1.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1
        every { repository.updateEmb(any(), any()) } throws RuntimeException("DB error")

        assertThrows<RuntimeException> {
            orchestrator.processOne(chunk)
        }

        verify(exactly = 1) { repository.updateEmb(any(), any()) }
    }

    @Test
    fun `processOne - обрабатывает пустой список handlers`() {
        val emptyOrchestrator = HandlerChainOrchestrator(repository, emptyList())
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        emptyOrchestrator.processOne(chunk)

        verify(exactly = 1) { repository.updateMeta(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processOne - не обновляет embedding если его нет в merged mutation`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val mutation = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "new_hash")
            .set(FieldKey.TOKEN_COUNT, 100)

        every { handler1.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) { repository.updateMeta(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { repository.updateEmb(any(), any()) }
    }

    @Test
    fun `processOne - правильно форматирует embedding literal`() {
        val chunk = createChunk(
            id = 1L,
            content = "test content",
        )

        val largeVector = FloatArray(5) { it.toFloat() } // [0.0, 1.0, 2.0, 3.0, 4.0]
        val mutation = PartialMutation()
            .set(FieldKey.EMB, largeVector)

        every { handler1.supports(any()) } returns true
        every { handler1.produce(any()) } returns mutation
        every { repository.updateMeta(any(), any(), any(), any(), any()) } returns 1
        every { repository.updateEmb(any(), any()) } returns 1

        orchestrator.processOne(chunk)

        verify(exactly = 1) {
            repository.updateEmb(
                id = 1L,
                embLiteral = "[0.0,1.0,2.0,3.0,4.0]",
            )
        }
    }

    private fun createChunk(
        id: Long,
        content: String,
        contentHash: String? = null,
        tokenCount: Int? = null,
        embedModel: String? = null,
        embedTs: OffsetDateTime? = null,
        emb: FloatArray? = null,
    ): Chunk {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L

        val node = Node(
            application = app,
            fqn = "com.example.Test",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node.id = 100L

        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "doc",
            content = content,
            contentHash = contentHash,
            tokenCount = tokenCount,
            embedModel = embedModel,
            embedTs = embedTs,
            emb = emb,
        )
    }
}
