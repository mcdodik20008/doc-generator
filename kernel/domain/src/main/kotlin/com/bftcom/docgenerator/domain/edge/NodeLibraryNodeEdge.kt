package com.bftcom.docgenerator.domain.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.library.LibraryNode
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
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Связь между узлом приложения (Node) и узлом библиотеки (LibraryNode).
 */
@Entity
@IdClass(NodeLibraryNodeEdgeId::class)
@Table(
    name = "node_library_node_edge",
    schema = "doc_generator",
    uniqueConstraints = [
        UniqueConstraint(
            name = "pk_node_library_node_edge",
            columnNames = ["node_id", "library_node_id", "kind"],
        ),
    ],
)
class NodeLibraryNodeEdge(
    // --- Составной ключ ---
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    var node: Node,
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_node_id", nullable = false)
    var libraryNode: LibraryNode,
    @Id
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(nullable = false, columnDefinition = "doc_generator.edge_kind")
    var kind: EdgeKind,
    // --- Свойства связи ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    var evidence: Map<String, Any> = emptyMap(),
    @Column(name = "explain_md", columnDefinition = "text")
    var explainMd: String? = null,
    @Column(name = "confidence", precision = 3, scale = 2)
    var confidence: BigDecimal? = null,
    @Column(name = "relation_strength")
    var relationStrength: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = OffsetDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

/**
 * Составной ключ для NodeLibraryNodeEdge.
 */
data class NodeLibraryNodeEdgeId(
    var node: Long? = null,
    var libraryNode: Long? = null,
    var kind: EdgeKind? = null,
) : java.io.Serializable

