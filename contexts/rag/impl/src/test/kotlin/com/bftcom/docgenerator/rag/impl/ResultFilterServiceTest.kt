package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResultFilterServiceTest {
    private lateinit var filterService: ResultFilterService

    @BeforeEach
    fun setUp() {
        filterService = ResultFilterService()
    }

    @Test
    fun `filterResults - без метаданных возвращает все результаты`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "Some content", 0.9),
            createSearchResult("2", "Other content", 0.8),
        )
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "session-1",
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(2)
        assertThat(filtered).containsExactlyElementsOf(results)
    }

    @Test
    fun `filterResults - фильтрация по классу с точным совпадением`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "class UserService { }", 0.9),
            createSearchResult("2", "class UserController { }", 0.8),
            createSearchResult("3", "class ProductService { }", 0.7),
        )
        val context = QueryProcessingContext(
            originalQuery = "UserService",
            currentQuery = "UserService",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "UserService"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
        assertThat(filtered[0].content).contains("UserService")
    }

    @Test
    fun `filterResults - фильтрация по методу с точным совпадением`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "fun getUser() { }", 0.9),
            createSearchResult("2", "fun getProduct() { }", 0.8),
            createSearchResult("3", "fun createUser() { }", 0.7),
        )
        val context = QueryProcessingContext(
            originalQuery = "getUser",
            currentQuery = "getUser",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("methodName" to "getUser"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
        assertThat(filtered[0].content).contains("getUser")
    }

    @Test
    fun `filterResults - фильтрация по классу и методу`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "class UserService { fun getUser() { } }", 0.9),
            createSearchResult("2", "class UserService { fun getProduct() { } }", 0.8),
            createSearchResult("3", "class ProductService { fun getUser() { } }", 0.7),
        )
        val context = QueryProcessingContext(
            originalQuery = "UserService getUser",
            currentQuery = "UserService getUser",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "UserService", "methodName" to "getUser"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
    }

    @Test
    fun `filterResults - нечеткое совпадение когда нет точных`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "UserServiceHelper class", 0.9),
            createSearchResult("2", "UserServiceManager class", 0.8),
            createSearchResult("3", "ProductService class", 0.7),
        )
        val context = QueryProcessingContext(
            originalQuery = "UserService",
            currentQuery = "UserService",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "UserService"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered.size).isGreaterThan(0)
        assertThat(filtered.all { it.content.contains("UserService", ignoreCase = true) }).isTrue
    }

    @Test
    fun `filterResults - использование имен из точных узлов`() {
        // Arrange
        val application = mockk<Application>()
        val exactNode = Node(
            id = 1L,
            application = application,
            fqn = "com.example.UserService",
            name = "UserService",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        
        val results = listOf(
            createSearchResult("1", "class UserService { }", 0.9),
            createSearchResult("2", "class ProductService { }", 0.8),
        )
        val context = QueryProcessingContext(
            originalQuery = "UserService",
            currentQuery = "UserService",
            sessionId = "session-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(exactNode))

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
    }

    @Test
    fun `filterResults - использование имени метода из точного узла`() {
        // Arrange
        val application = mockk<Application>()
        val exactNode = Node(
            id = 1L,
            application = application,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        
        val results = listOf(
            createSearchResult("1", "fun getUser() { }", 0.9),
            createSearchResult("2", "fun getProduct() { }", 0.8),
        )
        val context = QueryProcessingContext(
            originalQuery = "getUser",
            currentQuery = "getUser",
            sessionId = "session-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(exactNode))

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
    }

    @Test
    fun `filterResults - короткие ключевые слова пропускают фильтрацию`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "Content A", 0.9),
            createSearchResult("2", "Content B", 0.8),
        )
        val context = QueryProcessingContext(
            originalQuery = "A",
            currentQuery = "A",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "A"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(2)
    }

    @Test
    fun `filterResults - исключение похожих классов`() {
        // Arrange
        val results = listOf(
            createSearchResult("1", "class Step15Processor { }", 0.9),
            createSearchResult("2", "class Step16Processor { }", 0.8),
            createSearchResult("3", "class Step15ProcessorHelper { }", 0.7),
        )
        val context = QueryProcessingContext(
            originalQuery = "Step15Processor",
            currentQuery = "Step15Processor",
            sessionId = "session-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "Step15Processor"),
        )

        // Act
        val filtered = filterService.filterResults(results, context)

        // Assert
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].id).isEqualTo("1")
        assertThat(filtered[0].content).contains("Step15Processor")
        assertThat(filtered[0].content).doesNotContain("Step16Processor")
    }

    @Test
    fun `calculateRelevance - вычисление релевантности`() {
        // Arrange
        val content = "UserService class with getUser method and createUser method"
        val keywords = listOf("UserService", "getUser")

        // Act
        val relevance = filterService.calculateRelevance(content, keywords)

        // Assert
        assertThat(relevance).isGreaterThan(0.0)
    }

    @Test
    fun `calculateRelevance - пустой контент возвращает 0`() {
        // Arrange
        val content = ""
        val keywords = listOf("test")

        // Act
        val relevance = filterService.calculateRelevance(content, keywords)

        // Assert
        assertThat(relevance).isEqualTo(0.0)
    }

    @Test
    fun `calculateRelevance - без ключевых слов возвращает 0`() {
        // Arrange
        val content = "Some content"
        val keywords = emptyList<String>()

        // Act
        val relevance = filterService.calculateRelevance(content, keywords)

        // Assert
        assertThat(relevance).isEqualTo(0.0)
    }

    private fun createSearchResult(id: String, content: String, similarity: Double): SearchResult {
        return SearchResult(
            id = id,
            content = content,
            metadata = emptyMap(),
            similarity = similarity,
        )
    }
}
