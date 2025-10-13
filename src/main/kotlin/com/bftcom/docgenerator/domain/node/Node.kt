package com.bftcom.docgenerator.domain.node

import com.bftcom.docgenerator.domain.application.Application
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
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "node",
    schema = "doc_generator",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_node_app_fqn", columnNames = ["application_id", "fqn"]),
    ],
)
class Node(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    // --- Привязка к приложению ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    var application: Application,
    // --- Идентификация ---
    @Column(nullable = false)
    var fqn: String, // Fully Qualified Name, уникален внутри application
    @Column
    var name: String? = null,
    @Column
    var packageName: String? = null, // "package" — зарезервировано в Kotlin, поэтому packageName
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: NodeKind,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var lang: Lang,
    // --- Иерархия ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Node? = null,
    // --- Исходник/расположение ---
    @Column(name = "file_path")
    var filePath: String? = null,
    @Column(name = "line_start")
    var lineStart: Int? = null,
    @Column(name = "line_end")
    var lineEnd: Int? = null,
    @Column(name = "source_code", columnDefinition = "text")
    var sourceCode: String? = null,
    @Column(name = "doc_comment", columnDefinition = "text")
    var docComment: String? = null,
    // --- Сигнатура и контроль изменений ---
    @Column(name = "signature")
    var signature: String? = null,
    @Column(name = "code_hash")
    var codeHash: String? = null,
    // --- Произвольные атрибуты ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb", nullable = false)
    var meta: Map<String, Any> = emptyMap(),
    // --- Служебное ---
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
