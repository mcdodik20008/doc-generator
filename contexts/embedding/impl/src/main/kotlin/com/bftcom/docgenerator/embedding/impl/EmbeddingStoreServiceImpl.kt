package com.bftcom.docgenerator.embedding.impl

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.embedding.api.Document
import com.bftcom.docgenerator.embedding.api.EmbeddingStoreService
import org.springframework.ai.document.Document as AiDocument
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class EmbeddingStoreServiceImpl(
    private val vectorStore: VectorStore,
) : EmbeddingStoreService {

    override fun addDocument(id: String, content: String, metadata: Map<String, Any>) {
        val document = AiDocument.builder()
            .text(content)
            .metadata(metadata)
            .id(id)
            .build()
        vectorStore.add(listOf(document))
    }

    override fun addDocumentWithEmbedding(
        id: String,
        content: String,
        embedding: FloatArray,
        metadata: Map<String, Any>,
    ) {
        val document = AiDocument.builder()
            .text(content)
            .metadata(metadata)
            .id(id)
            .build()

        vectorStore.add(listOf(document))
    }

    override fun deleteDocument(id: String) {
        vectorStore.delete(listOf(id))
    }

    override fun getDocument(id: String): Document? {
        return null
    }

    override fun getAllDocuments(): List<Document> {
        return emptyList()
    }
}

