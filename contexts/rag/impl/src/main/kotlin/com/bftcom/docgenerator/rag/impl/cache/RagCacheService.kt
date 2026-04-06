package com.bftcom.docgenerator.rag.impl.cache

import com.bftcom.docgenerator.embedding.api.SearchResult
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Кэш для RAG-результатов: эмбеддинг-поиск и LLM-ответы.
 */
@Service
class RagCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${docgen.rag.cache.enabled:true}")
    private val cacheEnabled: Boolean = true,
    @Value("\${docgen.rag.cache.embedding-ttl-minutes:60}")
    private val embeddingTtlMinutes: Long = 60,
    @Value("\${docgen.rag.cache.response-ttl-minutes:15}")
    private val responseTtlMinutes: Long = 15,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getCachedEmbeddingResults(query: String, applicationId: Long?): List<SearchResult>? {
        if (!cacheEnabled) return null
        return try {
            val key = embeddingKey(query, applicationId)
            val json = redisTemplate.opsForValue().get(key) ?: return null
            objectMapper.readValue(json, object : TypeReference<List<SearchResult>>() {})
        } catch (e: Exception) {
            log.debug("Cache miss for embedding: {}", e.message)
            null
        }
    }

    fun cacheEmbeddingResults(query: String, applicationId: Long?, results: List<SearchResult>) {
        if (!cacheEnabled) return
        try {
            val key = embeddingKey(query, applicationId)
            val json = objectMapper.writeValueAsString(results)
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(embeddingTtlMinutes))
        } catch (e: Exception) {
            log.debug("Failed to cache embedding results: {}", e.message)
        }
    }

    fun getCachedResponse(query: String, sessionId: String): String? {
        if (!cacheEnabled) return null
        return try {
            val key = responseKey(query, sessionId)
            redisTemplate.opsForValue().get(key)
        } catch (e: Exception) {
            log.debug("Cache miss for response: {}", e.message)
            null
        }
    }

    fun cacheResponse(query: String, sessionId: String, response: String) {
        if (!cacheEnabled) return
        try {
            val key = responseKey(query, sessionId)
            redisTemplate.opsForValue().set(key, response, Duration.ofMinutes(responseTtlMinutes))
        } catch (e: Exception) {
            log.debug("Failed to cache response: {}", e.message)
        }
    }

    private fun embeddingKey(query: String, applicationId: Long?): String {
        val hash = query.hashCode().toUInt().toString(16)
        return "rag:emb:${applicationId ?: "all"}:$hash"
    }

    private fun responseKey(query: String, sessionId: String): String {
        val hash = query.hashCode().toUInt().toString(16)
        return "rag:resp:$sessionId:$hash"
    }
}
