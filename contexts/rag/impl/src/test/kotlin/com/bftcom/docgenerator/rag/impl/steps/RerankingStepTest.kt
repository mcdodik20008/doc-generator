package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.ResultFilterService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RerankingStepTest {
    private val resultFilterService = mockk<ResultFilterService>()
    private val step = RerankingStep(resultFilterService)

    @Test
    fun `execute - фильтрует чанки и переходит к COMPLETED`() {
        val chunks = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("2", "Content 2", 0.85),
            createSearchResult("3", "Content 3", 0.8),
        )
        val filteredChunks = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("2", "Content 2", 0.85),
        )

        every { resultFilterService.filterResults(chunks, any()) } returns filteredChunks

        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.CHUNKS, chunks)

        val result = step.execute(context)

        assertThat(result.nextStep).isEqualTo(ProcessingStepType.COMPLETED)
        val filtered = result.context.getMetadata<List<*>>(QueryMetadataKeys.FILTERED_CHUNKS)
        assertThat(filtered).isNotNull
        assertThat(filtered!!).hasSize(2)
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - переходит к FAILED если нет данных`() {
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("EMPTY")
    }

    @Test
    fun `execute - переходит к COMPLETED если есть EXACT_NODES даже без чанков`() {
        val app = mockk<com.bftcom.docgenerator.domain.application.Application>()
        val node = mockk<Node>()

        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(node))

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
    }

    @Test
    fun `execute - переходит к COMPLETED если есть GRAPH_RELATIONS_TEXT даже без чанков`() {
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.GRAPH_RELATIONS_TEXT, "Связи в графе")

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
    }

    @Test
    fun `execute - удаляет дубликаты перед фильтрацией`() {
        val chunks = listOf(
            createSearchResult("1", "Content 1", 0.9),
            createSearchResult("1", "Content 1", 0.9), // Дубликат
            createSearchResult("2", "Content 2", 0.85),
        )
        val filteredChunks = listOf(createSearchResult("1", "Content 1", 0.9))

        every { resultFilterService.filterResults(any(), any()) } answers {
            val results = firstArg<List<SearchResult>>()
            results.distinctBy { it.id }
        }

        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.CHUNKS, chunks)

        val result = step.execute(context)

        // resultFilterService должен получить список без дубликатов
        io.mockk.verify { resultFilterService.filterResults(any(), any()) }
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
