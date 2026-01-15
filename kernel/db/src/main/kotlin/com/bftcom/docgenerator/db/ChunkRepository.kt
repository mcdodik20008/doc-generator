package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.chunk.Chunk
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface ChunkRepository : JpaRepository<Chunk, Long> {
    fun findTopByNodeIdOrderByCreatedAtDesc(nodeId: Long): Chunk?

    @Query(
        value = """
            SELECT *
            FROM doc_generator.chunk c
            WHERE c.content_hash IS NULL
               OR c.token_count IS NULL
               OR (c.embedding IS NULL AND :withEmb = true)
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
                embed_model     = :embedModel,
                embed_ts        = :embedTs,
                updated_at      = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateMeta(
        @Param("id") id: Long,
        @Param("contentHash") contentHash: String,
        @Param("tokenCount") tokenCount: Int,
        @Param("embedModel") embedModel: String?, // может быть NULL, если emb выключен
        @Param("embedTs") embedTs: OffsetDateTime?, // может быть NULL
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE doc_generator.chunk
            SET embedding = CAST(:embLiteral AS vector),
                updated_at = NOW()
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmb(
        @Param("id") id: Long,
        @Param("embLiteral") embLiteral: String,
    ): Int

    fun findByNodeId(nodeId: Long): MutableList<Chunk>

    @Modifying
    @Query(
        value = """
            insert into doc_generator.chunk
              (application_id, node_id, source, kind, lang_detected, content, metadata, created_at, updated_at)
            values
              (:applicationId, :nodeId, 'doc', :kind, :locale, :content, cast(:metadataJson as jsonb), now(), now())
            on conflict (application_id, node_id, source, kind, lang_detected) do update
            set content = excluded.content,
                metadata = excluded.metadata,
                updated_at = excluded.updated_at
        """,
        nativeQuery = true,
    )
    fun upsertDocChunk(
        @Param("applicationId") applicationId: Long,
        @Param("nodeId") nodeId: Long,
        @Param("locale") locale: String,
        @Param("kind") kind: String, // public|tech
        @Param("content") content: String,
        @Param("metadataJson") metadataJson: String,
    ): Int
}
