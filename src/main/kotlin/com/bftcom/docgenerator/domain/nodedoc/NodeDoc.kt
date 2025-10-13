package com.bftcom.docgenerator.domain.nodedoc

import com.bftcom.docgenerator.domain.node.Node
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@IdClass(NodeDocId::class)
@Table(name = "node_doc", schema = "doc_generator")
class NodeDoc(
    // --- PK: (node_id, locale) ---
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    var node: Node,
    @Id
    @Column(name = "locale", nullable = false)
    var locale: String = "ru", // 'ru' | 'en' | 'ru-RU' ...
    // --- Контент ---
    @Column(name = "summary", columnDefinition = "text")
    var summary: String? = null,
    @Column(name = "details", columnDefinition = "text")
    var details: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb")
    var params: ParamsMap? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "returns", columnDefinition = "jsonb")
    var returns: TypeDesc? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "throws", columnDefinition = "jsonb")
    var throws_: ThrowsList? = null, // throws — ключевое слово, поэтому throws_
    @Column(name = "examples", columnDefinition = "text")
    var examples: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality", columnDefinition = "jsonb", nullable = false)
    var quality: QualityMap = emptyMap(),
    // --- Происхождение/авторство ---
    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false)
    var sourceKind: SourceKind = SourceKind.manual,
    @Column(name = "model_name")
    var modelName: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_meta", columnDefinition = "jsonb", nullable = false)
    var modelMeta: ModelMeta = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    var evidence: EvidenceMap = emptyMap(),
    @Column(name = "updated_by")
    var updatedBy: String? = null,
    // --- Поисковые STORED tsvector (read-only в JPA) ---
    @Column(name = "summary_tsv", insertable = false, updatable = false)
    var summaryTsv: String? = null,
    @Column(name = "details_tsv", insertable = false, updatable = false)
    var detailsTsv: String? = null,
    @Column(name = "examples_tsv", insertable = false, updatable = false)
    var examplesTsv: String? = null,
    // --- Публикация ---
    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = true,
    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
