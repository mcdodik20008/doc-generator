package com.bftcom.docgenerator.embedding.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class EmbeddingSearchServiceImpl(
    private val vectorStore: VectorStore,
    private val jdbcTemplate: JdbcTemplate,
    private val embeddingModel: EmbeddingModel,
    private val objectMapper: ObjectMapper,
    @Value("\${docgen.embedding.similarity-threshold:0.3}")
    private val defaultSimilarityThreshold: Double = 0.3,
) : EmbeddingSearchService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchByText(query: String, topK: Int, applicationId: Long?, similarityThreshold: Double): List<SearchResult> {
        val threshold = if (similarityThreshold > 0.0) similarityThreshold else defaultSimilarityThreshold
        if (applicationId == null) {
            return searchWithVectorStore(query, topK, threshold)
        }
        return searchWithApplicationFilter(query, topK, applicationId, threshold)
    }

    private fun searchWithVectorStore(query: String, topK: Int, threshold: Double): List<SearchResult> {
        val searchRequest = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(threshold)
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

    private fun searchWithApplicationFilter(query: String, topK: Int, applicationId: Long, threshold: Double): List<SearchResult> {
        val embedding = embeddingModel.embed(query)
        val embeddingStr = embedding.joinToString(",", "[", "]")

        val sql = """
            SELECT id, content, metadata,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM doc_generator.chunk
            WHERE application_id = ?
              AND (1 - (embedding <=> ?::vector)) >= ?
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
        }, embeddingStr, applicationId, embeddingStr, threshold, embeddingStr, topK)
    }

    // ===== Hybrid Search =====

    override fun hybridSearch(
        query: String,
        topK: Int,
        applicationId: Long?,
        ftsWeight: Double,
        vectorWeight: Double,
    ): List<SearchResult> {
        val threshold = defaultSimilarityThreshold
        val vectorResults = searchByText(query, topK = topK * 2, applicationId = applicationId, similarityThreshold = threshold)
        val ftsResults = searchWithFts(query, topK = topK * 2, applicationId = applicationId)

        val merged = reciprocalRankFusion(
            vectorResults = vectorResults,
            ftsResults = ftsResults,
            vectorWeight = vectorWeight,
            ftsWeight = ftsWeight,
        )

        log.debug("HYBRID_SEARCH: vector={}, fts={}, merged={}", vectorResults.size, ftsResults.size, merged.size)
        return merged.take(topK)
    }

    private fun searchWithFts(query: String, topK: Int, applicationId: Long?): List<SearchResult> {
        val tsQuery = query.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" & ")

        if (tsQuery.isBlank()) return emptyList()

        val sql = if (applicationId != null) {
            """
                SELECT id, content, metadata,
                       ts_rank_cd(content_tsv, to_tsquery('russian', ?)) AS rank
                FROM doc_generator.chunk
                WHERE application_id = ?
                  AND content_tsv @@ to_tsquery('russian', ?)
                ORDER BY rank DESC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT id, content, metadata,
                       ts_rank_cd(content_tsv, to_tsquery('russian', ?)) AS rank
                FROM doc_generator.chunk
                WHERE content_tsv @@ to_tsquery('russian', ?)
                ORDER BY rank DESC
                LIMIT ?
            """.trimIndent()
        }

        return try {
            val args = if (applicationId != null) {
                arrayOf(tsQuery, applicationId, tsQuery, topK)
            } else {
                arrayOf(tsQuery, tsQuery, topK)
            }

            jdbcTemplate.query(sql, { rs, _ ->
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
                    similarity = rs.getDouble("rank"),
                )
            }, *args)
        } catch (e: Exception) {
            log.warn("FTS search failed (content_tsv column may not exist): {}", e.message)
            emptyList()
        }
    }

    /**
     * Reciprocal Rank Fusion: объединяет два ранжированных списка.
     * score = weight / (k + rank + 1), где k = 60 (стандартное значение RRF).
     */
    private fun reciprocalRankFusion(
        vectorResults: List<SearchResult>,
        ftsResults: List<SearchResult>,
        vectorWeight: Double,
        ftsWeight: Double,
        k: Int = 60,
    ): List<SearchResult> {
        val scores = mutableMapOf<String, Double>()
        val resultMap = mutableMapOf<String, SearchResult>()

        vectorResults.forEachIndexed { rank, result ->
            val rrfScore = vectorWeight / (k + rank + 1)
            scores[result.id] = (scores[result.id] ?: 0.0) + rrfScore
            resultMap.putIfAbsent(result.id, result)
        }

        ftsResults.forEachIndexed { rank, result ->
            val rrfScore = ftsWeight / (k + rank + 1)
            scores[result.id] = (scores[result.id] ?: 0.0) + rrfScore
            resultMap.putIfAbsent(result.id, result)
        }

        return scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) ->
                resultMap[id]?.copy(similarity = score)
            }
    }
}
