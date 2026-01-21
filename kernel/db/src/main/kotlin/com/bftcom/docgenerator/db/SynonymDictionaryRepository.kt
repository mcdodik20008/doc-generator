package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.synonym.SynonymDictionary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SynonymDictionaryRepository : JpaRepository<SynonymDictionary, Long> {

    /**
     * Результат поиска с информацией о синониме и сходстве
     */
    interface SynonymWithSimilarity {
        fun getId(): Long
        fun getTerm(): String
        fun getDescription(): String
        fun getSourceNodeId(): Long
        fun getModelName(): String
    }

    /**
     * Проверка существования термина без учета регистра.
     * Используется для предотвращения дублирования смысловых пар.
     */
    fun existsByTermIgnoreCase(term: String): Boolean

    /**
     * Поиск топ-N кандидатов по косинусному сходству term_embedding с запросом.
     * Используется для первичного поиска синонимов.
     */
    @Query(
        value = """
            SELECT 
                s.id,
                s.term,
                s.description,
                s.source_node_id as sourceNodeId,
                s.model_name as modelName,
                1 - (s.term_embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM doc_generator.synonym_dictionary s
            WHERE s.term_embedding IS NOT NULL
            ORDER BY s.term_embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
        """,
        nativeQuery = true,
    )
    fun findTopByTermEmbedding(
        @Param("queryEmbedding") queryEmbedding: String, // "[1.0,2.0,...]" literal
        @Param("topK") topK: Int = 3,
    ): List<SynonymWithSimilarity>

    /**
     * Поиск синонимов с проверкой сходства desc_embedding.
     * Возвращает только те, где косинусное сходство >= threshold.
     * Косинусное расстояние <=> в pgvector: 0 = идентичны, 1 = ортогональны, 2 = противоположны
     * Косинусное сходство = 1 - расстояние
     */
    @Query(
        value = """
            SELECT 
                s.id,
                s.term,
                s.description,
                s.source_node_id as sourceNodeId,
                s.model_name as modelName,
                1 - (s.desc_embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM doc_generator.synonym_dictionary s
            WHERE s.desc_embedding IS NOT NULL
              AND (1 - (s.desc_embedding <=> CAST(:queryEmbedding AS vector))) >= :threshold
            ORDER BY s.desc_embedding <=> CAST(:queryEmbedding AS vector)
        """,
        nativeQuery = true,
    )
    fun findByDescEmbeddingWithThreshold(
        @Param("queryEmbedding") queryEmbedding: String,
        @Param("threshold") threshold: Double = 0.7,
    ): List<SynonymWithSimilarity>

    /**
     * Сохранение эмбеддингов для записи.
     */
    @Modifying
    @Query(
        value = """
            UPDATE doc_generator.synonym_dictionary
            SET term_embedding = CAST(:termEmbedding AS vector),
                desc_embedding = CAST(:descEmbedding AS vector),
                updated_at = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmbeddings(
        @Param("id") id: Long,
        @Param("termEmbedding") termEmbedding: String, // "[1.0,2.0,...]"
        @Param("descEmbedding") descEmbedding: String, // "[1.0,2.0,...]"
    ): Int

    /**
     * Поиск по source_node_id для удаления устаревших записей.
     */
    fun findBySourceNodeId(nodeId: Long): List<SynonymDictionary>

    /**
     * Удаление всех записей для указанного узла.
     */
    @Modifying
    @Query(
        value = """
            DELETE FROM doc_generator.synonym_dictionary
            WHERE source_node_id = :nodeId
        """,
        nativeQuery = true,
    )
    fun deleteBySourceNodeId(@Param("nodeId") nodeId: Long): Int
}
