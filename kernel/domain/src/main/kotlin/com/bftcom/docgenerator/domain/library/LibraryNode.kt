package com.bftcom.docgenerator.domain.library

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
import java.time.OffsetDateTime

/**
 * Узел графа внутри библиотеки (jar).
 * Структура аналогична Node, но привязана к Library, а не к Application.
 */
@Entity
@Table(
    name = "library_node",
    schema = "doc_generator",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_library_node_lib_fqn", columnNames = ["library_id", "fqn"]),
    ],
)
class LibraryNode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    // --- Привязка к библиотеке ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    var library: Library,
    // --- Идентификация ---
    @Column(nullable = false)
    var fqn: String, // уникален внутри library
    @Column
    var name: String? = null,
    @Column(name = "package")
    var packageName: String? = null,
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "kind", nullable = false, columnDefinition = "doc_generator.node_kind")
    var kind: NodeKind,
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "lang", nullable = false, columnDefinition = "doc_generator.lang")
    var lang: Lang,
    // --- Иерархия ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: LibraryNode? = null,
    // --- Исходник/расположение внутри jar ---
    @Column(name = "file_path")
    var filePath: String? = null,
    @Column(name = "line_start")
    var lineStart: Int? = null,
    @Column(name = "line_end")
    var lineEnd: Int? = null,
    // В библиотеке обычно нет исходников, но на всякий случай поле оставим
    @Column(name = "source_code", columnDefinition = "text")
    var sourceCode: String? = null,
    @Column(name = "doc_comment", columnDefinition = "text")
    var docComment: String? = null,
    // --- Сигнатура ---
    @Column(name = "signature")
    var signature: String? = null,
    // --- Произвольные атрибуты ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb", nullable = false)
    var meta: Map<String, Any> = emptyMap(),
    // --- Служебное ---
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
