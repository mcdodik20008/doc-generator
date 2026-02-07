package com.bftcom.docgenerator.embedding.impl

import com.bftcom.docgenerator.embedding.api.Document
import com.bftcom.docgenerator.embedding.api.EmbeddingStoreService
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document as AiDocument
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class EmbeddingStoreServiceImpl(
    private val vectorStore: VectorStore,
) : EmbeddingStoreService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun addDocument(id: String, content: String, metadata: Map<String, Any>) {
        try {
            val document = AiDocument.builder()
                .text(content)
                .metadata(metadata)
                .id(id)
                .build()
            vectorStore.add(listOf(document))
            log.debug("Document added successfully: id={}", id)
        } catch (e: Exception) {
            log.error("Failed to add document: id={}", id, e)
            throw e
        }
    }

    override fun addDocumentWithEmbedding(
        id: String,
        content: String,
        embedding: FloatArray,
        metadata: Map<String, Any>,
    ) {
        try {
            val enrichedMetadata = metadata.toMutableMap()
            enrichedMetadata["embedding"] = embedding.map { it.toDouble() }

            val document = AiDocument.builder()
                .text(content)
                .metadata(enrichedMetadata)
                .id(id)
                .build()

            vectorStore.add(listOf(document))
            log.debug("Document with embedding added successfully: id={}, embedding_size={}", id, embedding.size)
        } catch (e: Exception) {
            log.error("Failed to add document with embedding: id={}", id, e)
            throw e
        }
    }

    override fun deleteDocument(id: String) {
        try {
            vectorStore.delete(listOf(id))
        } catch (e: Exception) {
            log.error("Failed to delete document: id={}", id, e)
            throw e
        }
    }

    override fun getDocument(id: String): Document? {
        return try {
            // VectorStore не поддерживает прямой get по ID, поэтому используем similarity search
            // с очень высоким порогом сходства и фильтром по metadata
            val searchRequest = SearchRequest.builder()
                .query("")
                .topK(1)
                .filterExpression("id == '$id'")
                .build()

            val results = vectorStore.similaritySearch(searchRequest)

            if (results.isNotEmpty()) {
                val aiDoc = results.first()
                Document(
                    id = aiDoc.id,
                    content = aiDoc.text,
                    metadata = aiDoc.metadata
                )
            } else {
                log.debug("Document not found: id={}", id)
                null
            }
        } catch (e: Exception) {
            log.error("Failed to get document: id={}", id, e)
            null
        }
    }

    override fun getAllDocuments(): List<Document> {
        log.warn("getAllDocuments() is not efficiently supported by VectorStore - returning empty list")
        // VectorStore не предназначен для получения всех документов
        // Это может быть очень медленная операция для больших баз
        // Рекомендуется использовать отдельное хранилище для метаданных
        return emptyList()
    }
}

