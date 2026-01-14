package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import java.util.function.Consumer

class RagServiceImplTest {
    private lateinit var embeddingSearchService: EmbeddingSearchService
    private lateinit var chatClient: ChatClient
    private lateinit var queryProcessingChain: QueryProcessingChain
    private lateinit var resultFilterService: ResultFilterService
    private lateinit var ragService: RagServiceImpl

    @BeforeEach
    fun setUp() {
        embeddingSearchService = mockk()
        chatClient = mockk()
        queryProcessingChain = mockk()
        resultFilterService = mockk()
        ragService = RagServiceImpl(
            embeddingSearchService = embeddingSearchService,
            chatClient = chatClient,
            queryProcessingChain = queryProcessingChain,
            resultFilterService = resultFilterService,
        )
    }

    @Test
    fun `ask - базовый сценарий без дополнительных запросов`() {
        // Arrange
        val query = "Что делает метод process?"
        val sessionId = "session-123"
        
        val mainResults = listOf(
            createSearchResult("1", "Метод process обрабатывает данные", 0.9),
            createSearchResult("2", "Process method implementation", 0.85),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
        
        val chatResponse = createMockCallResponseSpec("Метод process обрабатывает входящие данные и возвращает результат.")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { resultFilterService.filterResults(mainResults, context) } returns mainResults
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.answer).isEqualTo("Метод process обрабатывает входящие данные и возвращает результат.")
        assertThat(result.sources).hasSize(2)
        assertThat(result.sources[0].id).isEqualTo("1")
        assertThat(result.metadata.originalQuery).isEqualTo(query)
        assertThat(result.metadata.rewrittenQuery).isNull()
        assertThat(result.metadata.expandedQueries).isEmpty()
        
        verify { queryProcessingChain.process(query, sessionId) }
        verify { embeddingSearchService.searchByText(query, topK = 5) }
        verify { resultFilterService.filterResults(mainResults, context) }
    }

    @Test
    fun `ask - с переформулированным запросом`() {
        // Arrange
        val query = "что делает process"
        val rewrittenQuery = "Что делает метод process?"
        val sessionId = "session-123"
        
        val mainResults = listOf(
            createSearchResult("1", "Метод process", 0.9),
        )
        
        val additionalResults = listOf(
            createSearchResult("3", "Process method details", 0.8),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        ).apply {
            setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, rewrittenQuery)
        }
        
        val chatResponse = createMockCallResponseSpec("Ответ на переформулированный запрос")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { embeddingSearchService.searchByText(rewrittenQuery, topK = 3) } returns additionalResults
        every { resultFilterService.filterResults(any(), context) } returns (mainResults + additionalResults).distinctBy { it.id }
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.metadata.rewrittenQuery).isEqualTo(rewrittenQuery)
        verify { embeddingSearchService.searchByText(rewrittenQuery, topK = 3) }
    }

    @Test
    fun `ask - с расширенными запросами`() {
        // Arrange
        val query = "как работает авторизация"
        val sessionId = "session-123"
        val expandedQueries = listOf("авторизация пользователя", "проверка прав доступа")
        
        val mainResults = listOf(
            createSearchResult("1", "Авторизация", 0.9),
        )
        
        val expandedResults1 = listOf(
            createSearchResult("2", "Авторизация пользователя", 0.85),
        )
        val expandedResults2 = listOf(
            createSearchResult("3", "Проверка прав", 0.8),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        ).apply {
            setMetadata(QueryMetadataKeys.EXPANDED_QUERIES, expandedQueries)
        }
        
        val chatResponse = createMockCallResponseSpec("Ответ с расширенными запросами")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { embeddingSearchService.searchByText(expandedQueries[0], topK = 3) } returns expandedResults1
        every { embeddingSearchService.searchByText(expandedQueries[1], topK = 3) } returns expandedResults2
        every { resultFilterService.filterResults(any(), context) } returns (mainResults + expandedResults1 + expandedResults2).distinctBy { it.id }
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.metadata.expandedQueries).containsExactlyElementsOf(expandedQueries)
        verify { embeddingSearchService.searchByText(expandedQueries[0], topK = 3) }
        verify { embeddingSearchService.searchByText(expandedQueries[1], topK = 3) }
    }

    @Test
    fun `ask - с точными узлами в контексте`() {
        // Arrange
        val query = "класс UserService"
        val sessionId = "session-123"
        
        val application = mockk<Application>()
        val exactNode = Node(
            id = 1L,
            application = application,
            fqn = "com.example.UserService",
            name = "UserService",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            sourceCode = "class UserService { }",
            docComment = "Сервис для работы с пользователями",
        )
        
        val mainResults = listOf(
            createSearchResult("1", "UserService class", 0.9),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        ).apply {
            setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(exactNode))
        }
        
        val chatResponse = createMockCallResponseSpec("Ответ с точными узлами")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { resultFilterService.filterResults(mainResults, context) } returns mainResults
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.answer).isEqualTo("Ответ с точными узлами")
        verify { chatClient.prompt() }
    }

    @Test
    fun `ask - с соседними узлами в контексте`() {
        // Arrange
        val query = "метод getUser"
        val sessionId = "session-123"
        
        val application = mockk<Application>()
        val neighborNode = Node(
            id = 2L,
            application = application,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            signature = "fun getUser(id: Long): User",
        )
        
        val mainResults = listOf(
            createSearchResult("1", "getUser method", 0.9),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        ).apply {
            setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, listOf(neighborNode))
        }
        
        val chatResponse = createMockCallResponseSpec("Ответ с соседними узлами")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { resultFilterService.filterResults(mainResults, context) } returns mainResults
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.answer).isEqualTo("Ответ с соседними узлами")
        verify { chatClient.prompt() }
    }

    @Test
    fun `ask - удаление дубликатов результатов`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-123"
        
        val mainResults = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("2", "Content 2", 0.85),
        )
        
        val additionalResults = listOf(
            createSearchResult("1", "Content 1", 0.9), // Дубликат
            createSearchResult("3", "Content 3", 0.8),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        ).apply {
            setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, "rewritten query")
        }
        
        val chatResponse = createMockCallResponseSpec("Ответ")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { embeddingSearchService.searchByText("rewritten query", topK = 3) } returns additionalResults
        every { resultFilterService.filterResults(any(), context) } answers {
            val results = firstArg<List<SearchResult>>()
            results.distinctBy { it.id }
        }
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        val allResults = mainResults + additionalResults
        val uniqueResults = allResults.distinctBy { it.id }
        assertThat(result.sources.size).isLessThanOrEqualTo(uniqueResults.size)
    }

    @Test
    fun `ask - ограничение результатов до топ-5`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-123"
        
        val manyResults = (1..10).map { i ->
            createSearchResult("$i", "Content $i", 1.0 - i * 0.1)
        }
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
        
        val chatResponse = createMockCallResponseSpec("Ответ")
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns manyResults
        every { resultFilterService.filterResults(any(), context) } answers { firstArg() }
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
        assertThat(result.sources.size).isLessThanOrEqualTo(5)
    }

    @Test
    fun `ask - обработка null ответа от chatClient`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-123"
        
        val mainResults = listOf(
            createSearchResult("1", "Content", 0.9),
        )
        
        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = sessionId,
        )
        
        val chatResponse = createMockCallResponseSpec(null)
        val promptSpec = createMockPromptSpec(chatResponse)
        
        every { queryProcessingChain.process(query, sessionId) } returns context
        every { embeddingSearchService.searchByText(query, topK = 5) } returns mainResults
        every { resultFilterService.filterResults(any(), any()) } returns mainResults
        every { chatClient.prompt() } returns promptSpec

        // Act
        val result = ragService.ask(query, sessionId)

        // Assert
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
        every { mock.advisors(*anyVararg<Advisor>()) } returns mock
        every { mock.call() } returns chatResponse
        return mock
    }
}
