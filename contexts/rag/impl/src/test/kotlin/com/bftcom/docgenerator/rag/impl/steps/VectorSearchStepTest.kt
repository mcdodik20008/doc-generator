package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VectorSearchStepTest {
    private val embeddingSearchService = mockk<EmbeddingSearchService>()
    private val step = VectorSearchStep(embeddingSearchService)

    @Test
    fun `execute - выполняет поиск по оригинальному запросу`() {
        val query = "Что делает метод process?"
        val results = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("2", "Content 2", 0.85),
        )

        every { embeddingSearchService.searchByText(query, topK = 5) } returns results

        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val chunks = result.context.getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
        assertThat(chunks).isNotNull
        assertThat(chunks!!).hasSize(2)
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - объединяет результаты оригинального и переформулированного запроса`() {
        val originalQuery = "что делает process"
        val rewrittenQuery = "Что делает метод process?"
        val originalResults = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("2", "Content 2", 0.85),
        )
        val rewrittenResults = listOf(
            createSearchResult("2", "Content 2", 0.85), // Дубликат
            createSearchResult("3", "Content 3", 0.8),
        )

        every { embeddingSearchService.searchByText(originalQuery, topK = 5) } returns originalResults
        every { embeddingSearchService.searchByText(rewrittenQuery, topK = 5) } returns rewrittenResults

        val context = QueryProcessingContext(
            originalQuery = originalQuery,
            currentQuery = rewrittenQuery,
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, rewrittenQuery)

        val result = step.execute(context)

        val chunks = result.context.getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
        assertThat(chunks).isNotNull
        // Должны быть удалены дубликаты
        assertThat(chunks!!).hasSize(3)
        assertThat(chunks.map { (it as SearchResult).id }).containsExactlyInAnyOrder("1", "2", "3")
    }

    @Test
    fun `execute - не использует переформулированный запрос если он совпадает с оригинальным`() {
        val query = "test query"
        val results = listOf(createSearchResult("1", "Content", 0.9))

        every { embeddingSearchService.searchByText(query, topK = 5) } returns results

        val context = QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, query) // Совпадает с оригинальным

        val result = step.execute(context)

        // Должен быть вызван только один раз
        io.mockk.verify(exactly = 1) { embeddingSearchService.searchByText(any(), any()) }
        val chunks = result.context.getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
        assertThat(chunks).isNotNull
        assertThat(chunks!!).hasSize(1)
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
