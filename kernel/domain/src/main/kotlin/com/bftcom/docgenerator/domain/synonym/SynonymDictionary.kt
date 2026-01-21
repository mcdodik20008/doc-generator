package com.bftcom.docgenerator.domain.synonym

import com.bftcom.docgenerator.domain.node.Node
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "synonym_dictionary", schema = "doc_generator")
class SynonymDictionary(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, columnDefinition = "text")
    var term: String,

    @Column(nullable = false, columnDefinition = "text")
    var description: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    var sourceNode: Node,

    // Векторные эмбеддинги хранятся как transient и сохраняются через нативные запросы
    @jakarta.persistence.Transient
    var termEmbedding: FloatArray? = null,

    @jakarta.persistence.Transient
    var descEmbedding: FloatArray? = null,

    @Column(name = "model_name", nullable = false)
    var modelName: String,

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
