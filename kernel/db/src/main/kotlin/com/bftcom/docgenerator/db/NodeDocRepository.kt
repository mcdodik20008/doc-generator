package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.nodedoc.NodeDoc
import com.bftcom.docgenerator.domain.nodedoc.NodeDocId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NodeDocRepository : JpaRepository<NodeDoc, NodeDocId> {
    @Query(
        value = """
            select d.doc_digest
            from doc_generator.node_doc d
            where d.node_id = :nodeId
              and d.locale = :locale
        """,
        nativeQuery = true,
    )
    fun findDigest(
        @Param("nodeId") nodeId: Long,
        @Param("locale") locale: String,
    ): String?

    @Modifying
    @Query(
        value = """
            insert into doc_generator.node_doc (node_id, locale, doc_public, doc_tech, doc_digest, model_meta, updated_at)
            values (:nodeId, :locale, :docPublic, :docTech, :docDigest, cast(:modelMetaJson as jsonb), now())
            on conflict (node_id, locale) do update
            set doc_public = excluded.doc_public,
                doc_tech   = excluded.doc_tech,
                doc_digest = excluded.doc_digest,
                model_meta = excluded.model_meta,
                updated_at = excluded.updated_at
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("nodeId") nodeId: Long,
        @Param("locale") locale: String,
        @Param("docPublic") docPublic: String?,
        @Param("docTech") docTech: String?,
        @Param("docDigest") docDigest: String?,
        @Param("modelMetaJson") modelMetaJson: String,
    ): Int

    interface DocChunkSyncRow {
        fun getNodeId(): Long
        fun getApplicationId(): Long
        fun getLocale(): String
        fun getDocPublic(): String?
        fun getDocTech(): String?
    }

    @Query(
        value = """
            select
              d.node_id as nodeId,
              n.application_id as applicationId,
              d.locale as locale,
              d.doc_public as docPublic,
              d.doc_tech as docTech
            from doc_generator.node_doc d
            join doc_generator.node n on n.id = d.node_id
            left join doc_generator.chunk cp
              on cp.application_id = n.application_id
             and cp.node_id = d.node_id
             and cp.source = 'doc'
             and cp.kind = 'public'
             and cp.lang_detected = d.locale
            left join doc_generator.chunk ct
              on ct.application_id = n.application_id
             and ct.node_id = d.node_id
             and ct.source = 'doc'
             and ct.kind = 'tech'
             and ct.lang_detected = d.locale
            where
              (
                d.doc_public is not null
                and (cp.id is null or cp.updated_at < d.updated_at)
              )
              or
              (
                d.doc_tech is not null
                and (ct.id is null or ct.updated_at < d.updated_at)
              )
            order by d.updated_at
            limit :limit
            for update of d skip locked
        """,
        nativeQuery = true,
    )
    fun lockNextBatchForChunkSync(@Param("limit") limit: Int): List<DocChunkSyncRow>
}

