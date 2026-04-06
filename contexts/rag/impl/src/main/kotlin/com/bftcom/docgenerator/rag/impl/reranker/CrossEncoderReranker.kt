package com.bftcom.docgenerator.rag.impl.reranker

import com.bftcom.docgenerator.embedding.api.SearchResult
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * Cross-encoder re-ranker через Ollama /api/rerank endpoint.
 * Использует bge-reranker-v2-m3 для переранжирования результатов поиска.
 */
@Component
class CrossEncoderReranker(
    @Value("\${docgen.rag.reranker.enabled:false}")
    private val enabled: Boolean,

    @Value("\${docgen.rag.reranker.model:bge-reranker-v2-m3}")
    private val model: String,

    @Value("\${docgen.rag.reranker.base-url:http://localhost:11434}")
    private val baseUrl: String,

    @Value("\${docgen.rag.reranker.top-n:10}")
    private val topN: Int,

    @Value("\${docgen.rag.reranker.timeout-seconds:15}")
    private val timeoutSeconds: Long,

    restTemplateBuilder: RestTemplateBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(timeoutSeconds))
        .build()

    fun isEnabled(): Boolean = enabled

    /**
     * Переранжирует результаты поиска с помощью cross-encoder модели.
     * Возвращает отсортированный список (до topN элементов) или исходный список при ошибке.
     */
    fun rerank(query: String, results: List<SearchResult>): List<SearchResult> {
        if (!enabled || results.isEmpty()) return results

        try {
            val documents = results.map { it.content }
            val request = RerankRequest(
                model = model,
                query = query,
                documents = documents,
                topN = topN.coerceAtMost(results.size),
            )

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(request, headers)

            val response = restTemplate.postForObject(
                "$baseUrl/api/rerank",
                entity,
                RerankResponse::class.java,
            )

            if (response?.results == null) {
                log.warn("Reranker returned null response, using original order")
                return results
            }

            // Map reranked indices back to SearchResult, replacing similarity with reranker score
            val reranked = response.results
                .sortedByDescending { it.relevanceScore }
                .mapNotNull { r ->
                    val idx = r.index
                    if (idx in results.indices) {
                        results[idx].copy(similarity = r.relevanceScore)
                    } else {
                        null
                    }
                }

            log.info(
                "Reranker: {} results reranked, top score={}, bottom score={}",
                reranked.size,
                reranked.firstOrNull()?.similarity,
                reranked.lastOrNull()?.similarity,
            )

            return reranked
        } catch (e: Exception) {
            log.warn("Reranker failed, falling back to original order: {}", e.message)
            return results
        }
    }

    // --- Ollama Rerank API DTOs ---

    data class RerankRequest(
        val model: String,
        val query: String,
        val documents: List<String>,
        @JsonProperty("top_n")
        val topN: Int,
    )

    data class RerankResponse(
        val results: List<RerankResult>?,
    )

    data class RerankResult(
        val index: Int,
        @JsonProperty("relevance_score")
        val relevanceScore: Double,
    )
}
