package com.bftcom.docgenerator.domain.nodedoc

import com.bftcom.docgenerator.domain.node.Node
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@IdClass(NodeDocId::class)
@Table(name = "node_doc", schema = "doc_generator")
class NodeDoc(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    var node: Node,

    @Id
    @Column(name = "locale", nullable = false)
    var locale: String = "ru",

    @Column(name = "doc_public", columnDefinition = "text")
    var docPublic: String? = null,

    @Column(name = "doc_tech", columnDefinition = "text")
    var docTech: String? = null,

    @Column(name = "doc_digest", columnDefinition = "text")
    var docDigest: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_meta", columnDefinition = "jsonb", nullable = false)
    var modelMeta: Map<String, Any> = emptyMap(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PrePersist
    fun prePersist() {
        updatedAt = OffsetDateTime.now()
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

