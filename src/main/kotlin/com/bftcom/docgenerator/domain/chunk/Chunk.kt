package com.bftcom.docgenerator.domain.chunk

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.node.Node
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "chunk", schema = "doc_generator")
class Chunk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    // --- FK ---
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    var application: Application,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    var node: Node,
    // --- тип/источник чанка ---
    @Column(nullable = false)
    var source: String, // 'code'|'doc'|'sql'|'log'
    @Column
    var kind: String? = null, // 'summary'|'explanation'|...
    @Column(name = "lang_detected")
    var langDetected: String? = null, // 'ru'|'en'|...
    // --- контент и дедуп ---
    @Column(name = "content_raw", columnDefinition = "text")
    var contentRaw: String? = null,
    @Column(columnDefinition = "text", nullable = false)
    var content: String,
    // tsvector STORED (генерация на стороне БД)
    @Column(name = "content_tsv", insertable = false, updatable = false)
    var contentTsv: String? = null,
    @Column(name = "content_hash")
    var contentHash: String? = null, // hex(SHA-256)
    @Column(name = "token_count")
    var tokenCount: Int? = null,
    // --- позиция/границы ---
    @Column(name = "chunk_index")
    var chunkIndex: Int? = null,
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "span_lines")
    var spanLines: String? = null, // int4range "[start,end]"
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "span_chars")
    var spanChars: String? = null, // int8range "[start,end]"
    // --- контекст ---
    @Column(name = "title")
    var title: String? = null,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "section_path", columnDefinition = "text[]", nullable = false)
    var sectionPath: List<String> = emptyList(),
    @Column(name = "uses_md", columnDefinition = "text")
    var usesMd: String? = null,
    @Column(name = "used_by_md", columnDefinition = "text")
    var usedByMd: String? = null,
    // --- вектор и модель ---
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "emb", columnDefinition = "vector(1024)")
    var emb: FloatArray? = null,
    @Column(name = "embed_model")
    var embedModel: String? = null,
    @Column(name = "embed_ts")
    var embedTs: OffsetDateTime? = null,
    // --- трактовка и качество ---
    @Column(name = "explain_md", columnDefinition = "text")
    var explainMd: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explain_quality", columnDefinition = "jsonb", nullable = false)
    var explainQuality: Map<String, Any> = emptyMap(),
    // --- связи (машиночитаемо) ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relations", columnDefinition = "jsonb", nullable = false)
    var relations: List<Map<String, Any>> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "used_objects", columnDefinition = "jsonb", nullable = false)
    var usedObjects: List<Map<String, Any>> = emptyList(),
    // --- происхождение пайплайна ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pipeline", columnDefinition = "jsonb", nullable = false)
    var pipeline: Map<String, Any> = emptyMap(),
    @Column(name = "freshness_at")
    var freshnessAt: OffsetDateTime? = null,
    @Column(name = "rank_boost", nullable = false)
    var rankBoost: Float = 1.0f,
    // --- служебное ---
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
