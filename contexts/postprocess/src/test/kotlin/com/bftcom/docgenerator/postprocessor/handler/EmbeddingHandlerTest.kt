package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.ai.chatclients.SummaryClient
import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.OffsetDateTime
import kotlin.test.assertTrue

class EmbeddingHandlerTest {

    private lateinit var embeddingClient: EmbeddingClient
    private lateinit var summaryClient: SummaryClient
    private lateinit var handler: EmbeddingHandler

    @BeforeEach
    fun setUp() {
        embeddingClient = mockk()
        summaryClient = mockk()
    }

    @Test
    fun `supports - возвращает false когда enabled = false`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = false,
            maxContentChars = 30000,
        )

        val snapshot = createSnapshot()
        
        assertThat(handler.supports(snapshot)).isFalse()
    }

    @Test
    fun `supports - возвращает false когда client = null`() {
        handler = EmbeddingHandler(
            client = null,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
        )

        val snapshot = createSnapshot()
        
        assertThat(handler.supports(snapshot)).isFalse()
    }

    @Test
    fun `supports - возвращает false когда embedding уже присутствует`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
        )

        val snapshot = createSnapshot(embeddingPresent = true)
        
        assertThat(handler.supports(snapshot)).isFalse()
    }

    @Test
    fun `supports - возвращает true когда все условия выполнены`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
        )

        val snapshot = createSnapshot(embeddingPresent = false)
        
        assertThat(handler.supports(snapshot)).isTrue()
    }

    @Test
    fun `produce - возвращает правильные поля для короткого контента`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
        )

        val content = "short content"
        val snapshot = createSnapshot(content = content)
        val expectedVector = floatArrayOf(1.0f, 2.0f, 3.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 3
        every { embeddingClient.embed(content) } returns expectedVector

        val result = handler.produce(snapshot)

        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("test-model")
        assertThat(result.provided[FieldKey.EMBED_TS]).isNotNull()
        assertThat(result.provided[FieldKey.EMBED_TS] is OffsetDateTime).isTrue()
    }

    @Test
    fun `produce - создает summary для длинного контента`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 100,
        )

        val longContent = "a".repeat(200)
        val summary = "summary of long content"
        val snapshot = createSnapshot(content = longContent)
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { summaryClient.summarize(longContent) } returns summary
        every { embeddingClient.embed(summary) } returns expectedVector

        val result = handler.produce(snapshot)

        verify(exactly = 1) { summaryClient.summarize(longContent) }
        verify(exactly = 1) { embeddingClient.embed(summary) }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - обрезает summary если он все еще слишком длинный`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 100,
        )

        val longContent = "a".repeat(200)
        val tooLongSummary = "b".repeat(150) // Длиннее maxContentChars
        val snapshot = createSnapshot(content = longContent)
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { summaryClient.summarize(longContent) } returns tooLongSummary
        every { embeddingClient.embed(tooLongSummary.take(100)) } returns expectedVector

        val result = handler.produce(snapshot)

        verify(exactly = 1) { summaryClient.summarize(longContent) }
        verify(exactly = 1) { embeddingClient.embed(tooLongSummary.take(100)) }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - проверяет размерность вектора`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
        )

        val snapshot = createSnapshot(content = "test")
        val wrongVector = floatArrayOf(1.0f, 2.0f) // Неправильная размерность

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 3
        every { embeddingClient.embed("test") } returns wrongVector

        val exception = assertThrows<IllegalArgumentException> {
            handler.produce(snapshot)
        }

        assertThat(exception.message).contains("Embedding dim 2 != expected 3")
    }

    @Test
    fun `produce - retry при EOFException`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10, // Короткая задержка для теста
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            if (callCount++ == 0) {
                throw EOFException("Connection closed")
            }
            expectedVector
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - retry при SocketException`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw SocketException("Connection reset")
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - retry при SocketTimeoutException`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw SocketTimeoutException("Read timed out")
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - выбрасывает исключение после исчерпания попыток`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 2,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { embeddingClient.embed("test") } throws EOFException("Connection closed")

        assertThrows<EOFException> {
            handler.produce(snapshot)
        }

        verify(exactly = 2) { embeddingClient.embed("test") }
    }

    @Test
    fun `produce - не retry при не-retryable ошибках`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { embeddingClient.embed("test") } throws IllegalArgumentException("Invalid input")

        assertThrows<IllegalArgumentException> {
            handler.produce(snapshot)
        }

        verify(exactly = 1) { embeddingClient.embed("test") }
    }

    @Test
    fun `produce - обрабатывает ошибки с EOF в сообщении`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Unexpected EOF while reading")
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - обрабатывает ошибки с Connection reset в сообщении`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Connection reset by peer")
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - обрабатывает ошибки с Broken pipe в сообщении`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Broken pipe")
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - обрабатывает вложенные исключения`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 10,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            callCount++
            if (callCount == 1) {
                throw RuntimeException("Outer", EOFException("Inner EOF"))
            } else {
                expectedVector
            }
        }

        val result = handler.produce(snapshot)

        verify(atLeast = 2) { embeddingClient.embed("test") }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - использует экспоненциальную задержку с джиттером`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 100,
        )

        val snapshot = createSnapshot(content = "test")
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        var callCount = 0
        every { embeddingClient.embed("test") } answers {
            if (callCount++ == 0) {
                throw EOFException("Connection closed")
            }
            expectedVector
        }

        val startTime = System.currentTimeMillis()
        val result = handler.produce(snapshot)
        val endTime = System.currentTimeMillis()

        // Проверяем, что была задержка (хотя бы 50ms с учетом джиттера)
        assertThat(endTime - startTime).isGreaterThan(50)
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - обрабатывает InterruptedException во время задержки`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 30000,
            maxRetryAttempts = 3,
            initialRetryDelayMs = 1000,
        )

        val snapshot = createSnapshot(content = "test")

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { embeddingClient.embed("test") } throws EOFException("Connection closed")
        
        // Прерываем текущий поток
        Thread.currentThread().interrupt()

        val exception = assertThrows<RuntimeException> {
            handler.produce(snapshot)
        }

        assertThat(exception.message).contains("Interrupted during retry delay")
        assertThat(exception.cause).isInstanceOf(InterruptedException::class.java)
        
        // Сбрасываем флаг прерывания
        Thread.interrupted()
    }

    @Test
    fun `produce - контент на границе maxContentChars не требует summary`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 100,
        )

        val content = "a".repeat(100) // Ровно maxContentChars
        val snapshot = createSnapshot(content = content)
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { embeddingClient.embed(content) } returns expectedVector

        val result = handler.produce(snapshot)

        verify(exactly = 0) { summaryClient.summarize(any()) }
        verify(exactly = 1) { embeddingClient.embed(content) }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    @Test
    fun `produce - контент длиннее maxContentChars на 1 символ требует summary`() {
        handler = EmbeddingHandler(
            client = embeddingClient,
            summaryClient = summaryClient,
            enabled = true,
            maxContentChars = 100,
        )

        val content = "a".repeat(101) // На 1 больше maxContentChars
        val summary = "summary"
        val snapshot = createSnapshot(content = content)
        val expectedVector = floatArrayOf(1.0f, 2.0f)

        every { embeddingClient.modelName } returns "test-model"
        every { embeddingClient.dim } returns 2
        every { summaryClient.summarize(content) } returns summary
        every { embeddingClient.embed(summary) } returns expectedVector

        val result = handler.produce(snapshot)

        verify(exactly = 1) { summaryClient.summarize(content) }
        verify(exactly = 1) { embeddingClient.embed(summary) }
        assertThat(result.provided[FieldKey.EMB]).isEqualTo(expectedVector)
    }

    private fun createSnapshot(
        id: Long = 1L,
        content: String = "test content",
        contentHash: String? = null,
        tokenCount: Int? = null,
        embeddingPresent: Boolean = false,
        embedModel: String? = null,
        embedTs: OffsetDateTime? = null,
    ): ChunkSnapshot {
        return ChunkSnapshot(
            id = id,
            content = content,
            contentHash = contentHash,
            tokenCount = tokenCount,
            embeddingPresent = embeddingPresent,
            embedModel = embedModel,
            embedTs = embedTs,
        )
    }
}

