package com.bftcom.docgenerator.embedding.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class EmbeddingSearchServiceImpl(
    private val vectorStore: VectorStore,
    private val jdbcTemplate: JdbcTemplate,
    private val embeddingModel: EmbeddingModel,
    private val objectMapper: ObjectMapper,
) : EmbeddingSearchService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchByText(query: String, topK: Int, applicationId: Long?): List<SearchResult> {
        if (applicationId == null) {
            return searchWithVectorStore(query, topK)
        }
        return searchWithApplicationFilter(query, topK, applicationId)
    }

    private fun searchWithVectorStore(query: String, topK: Int): List<SearchResult> {
        val searchRequest = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build()

        val results = vectorStore.similaritySearch(searchRequest)

        return results.map { doc: Document ->
            SearchResult(
                id = doc.id ?: "",
                content = doc.text ?: "",
                metadata = doc.metadata,
                similarity = doc.score ?: 0.0
            )
        }
    }

    private fun searchWithApplicationFilter(query: String, topK: Int, applicationId: Long): List<SearchResult> {
        val embedding = embeddingModel.embed(query)
        val embeddingStr = embedding.joinToString(",", "[", "]")

        val sql = """
            SELECT id, content, metadata,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM doc_generator.chunk
            WHERE application_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val metadataJson = rs.getString("metadata")
            val metadata: Map<String, Any> = try {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(metadataJson, Map::class.java) as Map<String, Any>
            } catch (_: Exception) {
                emptyMap()
            }

            SearchResult(
                id = rs.getString("id") ?: "",
                content = rs.getString("content") ?: "",
                metadata = metadata,
                similarity = rs.getDouble("similarity"),
            )
        }, embeddingStr, applicationId, embeddingStr, topK)
    }
}
