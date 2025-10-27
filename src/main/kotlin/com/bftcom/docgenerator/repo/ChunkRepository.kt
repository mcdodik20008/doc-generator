package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.chunk.Chunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

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

    @Query(
        value = """
            SELECT *
            FROM doc_generator.chunk c
            WHERE c.content IS NOT NULL
              AND (
                   c.content_hash IS NULL
                OR c.token_count IS NULL
                OR c.span_chars IS NULL
                OR c.uses_md IS NULL
                OR c.used_by_md IS NULL
                OR c.explain_md IS NULL
                OR (c.emb IS NULL AND :withEmb = true)
              )
            ORDER BY c.created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockNextBatchForPostprocess(
        @Param("limit") limit: Int,
        @Param("withEmb") withEmb: Boolean,
    ): List<Chunk>

    @Modifying
    @Query(
        value = """
            UPDATE doc_generator.chunk
            SET content_hash    = :contentHash,
                token_count     = :tokenCount,
                span_chars      = CAST(:spanChars AS int8range),
                uses_md         = :usesMd,
                used_by_md      = :usedByMd,
                embed_model     = :embedModel,
                embed_ts        = :embedTs,
                explain_md      = :explainMd,
                explain_quality = CAST(:explainQuality AS jsonb),
                updated_at      = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updatePostMeta(
        @Param("id") id: Long,
        @Param("contentHash") contentHash: String,
        @Param("tokenCount") tokenCount: Int,
        @Param("spanChars") spanChars: String,
        @Param("usesMd") usesMd: String?,          // "md" или NULL
        @Param("usedByMd") usedByMd: String?,      // "md" или NULL
        @Param("embedModel") embedModel: String?,  // может быть NULL, если emb выключен
        @Param("embedTs") embedTs: OffsetDateTime?,// может быть NULL
        @Param("explainMd") explainMd: String,
        @Param("explainQuality") explainQualityJson: String,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE doc_generator.chunk
            SET emb = CAST(:embLiteral AS vector),
                updated_at = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmb(@Param("id") id: Long, @Param("embLiteral") embLiteral: String): Int
    fun findByNodeId(nodeId: Long): MutableList<Chunk>
}
