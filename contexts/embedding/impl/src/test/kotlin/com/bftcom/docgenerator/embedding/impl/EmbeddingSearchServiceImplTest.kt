package com.bftcom.docgenerator.embedding.impl

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore

class EmbeddingSearchServiceImplTest {
    @Test
    fun `searchByText - маппит результаты similaritySearch`() {
        val store = mockk<VectorStore>()
        val service = EmbeddingSearchServiceImpl(store)

        val d1 = Document.builder().id("1").text("t1").score(0.9).metadata(mapOf("k" to "v")).build()
        val d2 = Document.builder().id("2").text("t2").score(0.0).metadata(emptyMap<String, Any>()).build()

        every { store.similaritySearch(any<SearchRequest>()) } returns listOf(d1, d2)

        val res = service.searchByText(query = "q", topK = 2)

        assertThat(res).hasSize(2)
        assertThat(res[0].id).isEqualTo("1")
        assertThat(res[0].content).isEqualTo("t1")
        assertThat(res[0].similarity).isEqualTo(0.9)
        assertThat(res[0].metadata).containsEntry("k", "v")

        assertThat(res[1].id).isEqualTo("2")
        assertThat(res[1].content).isEqualTo("t2")
        assertThat(res[1].similarity).isEqualTo(0.0)
    }
}

