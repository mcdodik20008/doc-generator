package com.bftcom.docgenerator.embedding.impl

import io.mockk.slot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.VectorStore

class EmbeddingStoreServiceImplTest {
    @Test
    fun `addDocument - добавляет документ в VectorStore`() {
        val store = mockk<VectorStore>(relaxed = true)
        val service = EmbeddingStoreServiceImpl(store)

        val docsSlot = slot<List<org.springframework.ai.document.Document>>()
        every { store.add(capture(docsSlot)) } returns Unit

        service.addDocument(id = "id1", content = "text", metadata = mapOf("a" to 1))

        verify(exactly = 1) { store.add(any()) }
        val doc = docsSlot.captured.single()
        assertThat(doc.id).isEqualTo("id1")
        assertThat(doc.text).isEqualTo("text")
        assertThat(doc.metadata).containsEntry("a", 1)
    }

    @Test
    fun `addDocumentWithEmbedding - сейчас просто добавляет документ`() {
        val store = mockk<VectorStore>(relaxed = true)
        val service = EmbeddingStoreServiceImpl(store)

        service.addDocumentWithEmbedding(
            id = "id1",
            content = "text",
            embedding = floatArrayOf(0.1f, 0.2f),
            metadata = mapOf("a" to 1),
        )

        verify(exactly = 1) { store.add(any()) }
    }

    @Test
    fun `deleteDocument - удаляет по id`() {
        val store = mockk<VectorStore>(relaxed = true)
        val service = EmbeddingStoreServiceImpl(store)

        service.deleteDocument("id1")

        verify(exactly = 1) { store.delete(listOf("id1")) }
    }

    @Test
    fun `getDocument - возвращает null если документ не найден`() {
        val store = mockk<VectorStore>(relaxed = true)
        every { store.similaritySearch(any<org.springframework.ai.vectorstore.SearchRequest>()) } returns emptyList()
        val service = EmbeddingStoreServiceImpl(store)

        assertThat(service.getDocument("id1")).isNull()
    }

    @Test
    fun `getAllDocuments - возвращает пустой список`() {
        val store = mockk<VectorStore>(relaxed = true)
        val service = EmbeddingStoreServiceImpl(store)

        assertThat(service.getAllDocuments()).isEmpty()
    }
}

