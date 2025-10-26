package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.chunk.Chunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChunkRepository : JpaRepository<Chunk, Long> {
    @Query(
        value = """
            SELECT *
            FROM doc_generator.chunk
            WHERE content_raw IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockNextBatchForRawFill(
        @Param("limit") limit: Int,
    ): List<Chunk>

    @Query(
        value = """
            SELECT *
            FROM doc_generator.chunk
            WHERE content_raw IS NOT NULL and content = 'null'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockNextBatchContentForFill(
        @Param("limit") limit: Int,
    ): List<Chunk>

    fun findTopByNodeIdOrderByCreatedAtDesc(nodeId: Long): Chunk?
}
