package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.util.function.Consumer

class RagServiceImplTest {
    private lateinit var chatClient: ChatClient
    private lateinit var graphRequestProcessor: GraphRequestProcessor
    private lateinit var ragService: RagServiceImpl

    @BeforeEach
    fun setUp() {
        chatClient = mockk()
        graphRequestProcessor = mockk()
        ragService = RagServiceImpl(
            chatClient = chatClient,
            graphRequestProcessor = graphRequestProcessor,
        )
    }

    @Test
    fun `ask - базовый сценарий с найденными чанками`() {
        val query = "Что делает метод process?"
        val sessionId = "session-123"
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
            .setMetadata(QueryMetadataKeys.FILTERED_CHUNKS, listOf(
                createSearchResult("1", "Метод process обрабатывает данные", 0.9),
                createSearchResult("2", "Process method implementation", 0.85),
            ))
            .setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.COMPLETED.name)

        val chatResponse = createMockCallResponseSpec("Метод process обрабатывает входящие данные и возвращает результат.")
        val promptSpec = createMockPromptSpec(chatResponse)

        every { graphRequestProcessor.process(query, sessionId, null) } returns context
        every { chatClient.prompt() } returns promptSpec

        val result = ragService.ask(query, sessionId)

        assertThat(result.answer).isEqualTo("Метод process обрабатывает входящие данные и возвращает результат.")
        assertThat(result.sources).hasSize(2)
        assertThat(result.metadata.originalQuery).isEqualTo(query)
        verify { graphRequestProcessor.process(query, sessionId, null) }
    }

    @Test
    fun `ask - возвращает дефолтный ответ при FAILED`() {
        val query = "неизвестный запрос"
        val sessionId = "session-123"
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
            metadata = mutableMapOf(QueryMetadataKeys.PROCESSING_STATUS.key to ProcessingStepType.FAILED.name),
        )

        every { graphRequestProcessor.process(query, sessionId, null) } returns context

        val result = ragService.ask(query, sessionId)

        assertThat(result.answer).isEqualTo("Информация не найдена")
        assertThat(result.sources).isEmpty()
        verify(exactly = 0) { chatClient.prompt() }
    }

    @Test
    fun `ask - сохраняет rewrittenQuery в метаданных`() {
        val query = "что делает process"
        val sessionId = "session-123"
        val rewrittenQuery = "Что делает метод process?"
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
            .setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, rewrittenQuery)
            .setMetadata(QueryMetadataKeys.FILTERED_CHUNKS, listOf(
                createSearchResult("1", "Content", 0.9),
            ))
            .setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.COMPLETED.name)

        val chatResponse = createMockCallResponseSpec("Ответ")
        val promptSpec = createMockPromptSpec(chatResponse)

        every { graphRequestProcessor.process(query, sessionId, null) } returns context
        every { chatClient.prompt() } returns promptSpec

        val result = ragService.ask(query, sessionId)

        assertThat(result.metadata.rewrittenQuery).isEqualTo(rewrittenQuery)
    }

    @Test
    fun `ask - удаляет дубликаты результатов`() {
        val query = "test query"
        val sessionId = "session-123"
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
            .setMetadata(QueryMetadataKeys.CHUNKS, listOf(
                createSearchResult("1", "Content 1", 0.9),
                createSearchResult("1", "Content 1", 0.9),
                createSearchResult("2", "Content 2", 0.85),
            ))
            .setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.COMPLETED.name)

        val chatResponse = createMockCallResponseSpec("Ответ")
        val promptSpec = createMockPromptSpec(chatResponse)

        every { graphRequestProcessor.process(query, sessionId, null) } returns context
        every { chatClient.prompt() } returns promptSpec

        val result = ragService.ask(query, sessionId)

        assertThat(result.sources).hasSize(2)
    }

    @Test
    fun `ask - ограничивает результаты до топ-5`() {
        val query = "test query"
        val sessionId = "session-123"
        val manyResults = (1..10).map { i ->
            createSearchResult("$i", "Content $i", 1.0 - i * 0.05)
        }
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
            .setMetadata(QueryMetadataKeys.FILTERED_CHUNKS, manyResults)
            .setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.COMPLETED.name)

        val chatResponse = createMockCallResponseSpec("Ответ")
        val promptSpec = createMockPromptSpec(chatResponse)

        every { graphRequestProcessor.process(query, sessionId, null) } returns context
        every { chatClient.prompt() } returns promptSpec

        val result = ragService.ask(query, sessionId)

        assertThat(result.sources.size).isLessThanOrEqualTo(5)
    }

    @Test
    fun `ask - обработка null ответа от chatClient`() {
        val query = "test query"
        val sessionId = "session-123"
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
            .setMetadata(QueryMetadataKeys.FILTERED_CHUNKS, listOf(createSearchResult("1", "Content", 0.9)))
            .setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.COMPLETED.name)

        val chatResponse = createMockCallResponseSpec(null)
        val promptSpec = createMockPromptSpec(chatResponse)

        every { graphRequestProcessor.process(query, sessionId, null) } returns context
        every { chatClient.prompt() } returns promptSpec

        val result = ragService.ask(query, sessionId)

        assertThat(result.answer).isEqualTo("Не удалось получить ответ.")
    }

    private fun createSearchResult(id: String, content: String, similarity: Double): SearchResult {
        return SearchResult(
            id = id,
            content = content,
            metadata = emptyMap(),
            similarity = similarity,
        )
    }

    private fun createMockCallResponseSpec(content: String?): ChatClient.CallResponseSpec {
        val mock = mockk<ChatClient.CallResponseSpec>()
        every { mock.content() } returns content
        return mock
    }

    private fun createMockPromptSpec(chatResponse: ChatClient.CallResponseSpec): ChatClient.ChatClientRequestSpec {
        val mock = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { mock.user(any<String>()) } returns mock
        every { mock.advisors(any<Consumer<ChatClient.AdvisorSpec>>()) } returns mock
        every { mock.call() } returns chatResponse
        return mock
    }
}
