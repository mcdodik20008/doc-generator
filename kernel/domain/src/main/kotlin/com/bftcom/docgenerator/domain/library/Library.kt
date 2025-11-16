package com.bftcom.docgenerator.domain.library

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Библиотека/артефакт (jar), который может использоваться несколькими приложениями.
 * Пример: groupId:artifactId:version.
 */
@Entity
@Table(name = "library", schema = "doc_generator")
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** Полный координат артефакта: groupId:artifactId:version */
    @Column(nullable = false, unique = true)
    var coordinate: String,

    /** groupId, например org.springframework */
    @Column(name = "group_id", nullable = false)
    var groupId: String,

    /** artifactId, например spring-webflux */
    @Column(name = "artifact_id", nullable = false)
    var artifactId: String,

    /** Версия артефакта */
    @Column(name = "version", nullable = false)
    var version: String,

    /** Тип библиотеки: internal/external/system и т.п. */
    @Column(name = "kind")
    var kind: String? = null,

    /** Свободные метаданные (например, ссылки на исходники, POM, лицензии) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: Map<String, Any> = emptyMap(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)


