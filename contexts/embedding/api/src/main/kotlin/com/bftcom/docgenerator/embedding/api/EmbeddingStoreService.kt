package com.bftcom.docgenerator.embedding.api

/**
 * Сервис для сохранения документов с эмбеддингами
 */
interface EmbeddingStoreService {
    /**
     * Добавить документ с текстом (эмбеддинг будет вычислен автоматически)
     * @param id уникальный идентификатор документа
     * @param content текст документа
     * @param metadata дополнительные метаданные
     */
    fun addDocument(id: String, content: String, metadata: Map<String, Any> = emptyMap())

    /**
     * Добавить документ с готовым эмбеддингом
     * @param id уникальный идентификатор документа
     * @param content текст документа
     * @param embedding вектор эмбеддинга
     * @param metadata дополнительные метаданные
     */
    fun addDocumentWithEmbedding(
        id: String,
        content: String,
        embedding: FloatArray,
        metadata: Map<String, Any> = emptyMap(),
    )

    /**
     * Удалить документ по идентификатору
     * @param id идентификатор документа
     */
    fun deleteDocument(id: String)

    /**
     * Получить документ по идентификатору
     * @param id идентификатор документа
     * @return документ или null если не найден
     */
    fun getDocument(id: String): Document?

    /**
     * Получить все документы
     * @return список всех документов
     */
    fun getAllDocuments(): List<Document>
}

/**
 * Документ с эмбеддингом
 */
data class Document(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
)

