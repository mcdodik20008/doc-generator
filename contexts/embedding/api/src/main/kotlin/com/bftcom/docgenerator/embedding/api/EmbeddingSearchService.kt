package com.bftcom.docgenerator.embedding.api

/**
 * Сервис для поиска документов по эмбеддингам
 */
interface EmbeddingSearchService {
    /**
     * Поиск похожих документов по текстовому запросу
     * @param query текстовый запрос
     * @param topK количество результатов
     * @return список найденных документов с метаданными
     */
    fun searchByText(query: String, topK: Int = 10): List<SearchResult>

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

