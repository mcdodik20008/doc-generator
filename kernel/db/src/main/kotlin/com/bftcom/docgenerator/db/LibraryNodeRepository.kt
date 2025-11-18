package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.library.LibraryNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LibraryNodeRepository : JpaRepository<LibraryNode, Long> {
    fun findByLibraryIdAndFqn(libraryId: Long, fqn: String): LibraryNode?
    
    /**
     * Находит все методы библиотеки, которые являются родительскими клиентами.
     */
    @Query(
        value = """
            SELECT ln.*
            FROM doc_generator.library_node ln
            WHERE ln.library_id = :libraryId
              AND ln.kind = 'METHOD'
              AND ((ln.meta -> 'integrationAnalysis' ->> 'isParentClient')::boolean) = true
        """,
        nativeQuery = true
    )
    fun findParentClientsByLibraryId(libraryId: Long): List<LibraryNode>
    
    /**
     * Находит все методы, которые используют указанный URL (через JSONB поиск).
     */
    @Query(
        value = """
        SELECT * FROM doc_generator.library_node ln 
        WHERE (:libraryId IS NULL OR ln.library_id = :libraryId)
        AND ln.kind = 'METHOD'
        AND ln.meta->'integrationAnalysis'->'urls' @> :urlJson::jsonb
        """,
        nativeQuery = true,
    )
    fun findMethodsByUrl(
        @Param("urlJson") urlJson: String,
        @Param("libraryId") libraryId: Long?,
    ): List<LibraryNode>
    
    /**
     * Находит все методы, которые используют указанный Kafka topic.
     */
    @Query(
        value = """
        SELECT * FROM doc_generator.library_node ln 
        WHERE (:libraryId IS NULL OR ln.library_id = :libraryId)
        AND ln.kind = 'METHOD'
        AND ln.meta->'integrationAnalysis'->'kafkaTopics' @> :topicJson::jsonb
        """,
        nativeQuery = true,
    )
    fun findMethodsByKafkaTopic(
        @Param("topicJson") topicJson: String,
        @Param("libraryId") libraryId: Long?,
    ): List<LibraryNode>
    
    /**
     * Находит все методы, которые используют указанный Camel URI.
     */
    @Query(
        value = """
        SELECT * FROM doc_generator.library_node ln 
        WHERE (:libraryId IS NULL OR ln.library_id = :libraryId)
        AND ln.kind = 'METHOD'
        AND ln.meta->'integrationAnalysis'->'camelUris' @> :uriJson::jsonb
        """,
        nativeQuery = true,
    )
    fun findMethodsByCamelUri(
        @Param("uriJson") uriJson: String,
        @Param("libraryId") libraryId: Long?,
    ): List<LibraryNode>
}

