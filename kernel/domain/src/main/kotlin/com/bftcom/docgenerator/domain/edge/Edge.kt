package com.bftcom.docgenerator.domain.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
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
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@IdClass(EdgeId::class)
@Table(
    name = "edge",
    schema = "doc_generator",
    uniqueConstraints = [
        UniqueConstraint(
            name = "pk_edge_src_dst_kind",
            columnNames = ["src_id", "dst_id", "kind"],
        ),
    ],
)
class Edge(
    // --- Составной ключ ---
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "src_id", nullable = false)
    var src: Node,
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dst_id", nullable = false)
    var dst: Node,
    @Id
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class) // <-- ДОБАВЬТЕ ЭТУ СТРОКУ
    @Column(nullable = false, columnDefinition = "doc_generator.edge_kind")
    var kind: EdgeKind,
    // --- Свойства связи ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    var evidence: Map<String, Any> = emptyMap(),
    @Column(name = "explain_md", columnDefinition = "text")
    var explainMd: String? = null, // трактовка LLM в Markdown
    @Column(name = "confidence", precision = 3, scale = 2)
    var confidence: BigDecimal? = null, // 0.00–1.00
    @Column(name = "relation_strength")
    var relationStrength: String? = null, // weak|normal|strong
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
