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
    var langDetected: String = "ru", // 'ru'|'en'|...
    // --- контент и дедуп ---
    @Column(columnDefinition = "text", nullable = false)
    var content: String,
    // tsvector STORED (генерация на стороне БД)
    @Column(name = "content_tsv", insertable = false, updatable = false)
    var contentTsv: String? = null,
    @Column(name = "content_hash")
    var contentHash: String? = null, // hex(SHA-256)
    @Column(name = "token_count")
    var tokenCount: Int? = null,
    // --- вектор и модель ---
    @jakarta.persistence.Transient
    var emb: FloatArray? = null,
    @Column(name = "embed_model")
    var embedModel: String? = null,
    @Column(name = "embed_ts")
    var embedTs: OffsetDateTime? = null,
    // --- метаданные для Spring AI PgVectorStore ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: Map<String, Any> = emptyMap(),
    // --- служебное ---
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
