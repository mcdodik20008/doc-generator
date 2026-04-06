package com.bftcom.docgenerator.embedding.api

/**
 * Сервис для поиска документов по эмбеддингам
 */
interface EmbeddingSearchService {
    /**
     * Поиск похожих документов по текстовому запросу
     * @param query текстовый запрос
     * @param topK количество результатов
     * @param applicationId опциональный ID приложения для фильтрации
     * @return список найденных документов с метаданными
     */
    fun searchByText(query: String, topK: Int = 10, applicationId: Long? = null, similarityThreshold: Double = 0.0): List<SearchResult>

    /**
     * Гибридный поиск: FTS + Vector + Reciprocal Rank Fusion.
     * @param query текстовый запрос
     * @param topK количество результатов
     * @param applicationId опциональный ID приложения для фильтрации
     * @param ftsWeight вес FTS-результатов в RRF (по умолчанию 0.4)
     * @param vectorWeight вес Vector-результатов в RRF (по умолчанию 0.6)
     */
    fun hybridSearch(
        query: String,
        topK: Int = 10,
        applicationId: Long? = null,
        ftsWeight: Double = 0.4,
        vectorWeight: Double = 0.6,
    ): List<SearchResult>
}

/**
 * Результат поиска
 */
data class SearchResult(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
    val similarity: Double,
)

