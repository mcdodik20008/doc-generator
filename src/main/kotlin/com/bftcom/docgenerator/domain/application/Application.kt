package com.bftcom.docgenerator.domain.application

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "application", schema = "doc_generator")
class Application(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    // --- Идентичность ---
    @Column(nullable = false, unique = true)
    var key: String,
    @Column(nullable = false)
    var name: String,
    @Column(columnDefinition = "text")
    var description: String? = null,
    // --- Репозиторий ---
    @Column(name = "repo_url")
    var repoUrl: String? = null,
    @Column(name = "repo_provider")
    var repoProvider: String? = null, // github|gitlab|bitbucket|gitea|other
    @Column(name = "repo_owner")
    var repoOwner: String? = null,
    @Column(name = "repo_name")
    var repoName: String? = null,
    @Column(name = "monorepo_path")
    var monorepoPath: String? = null,
    @Column(name = "default_branch", nullable = false)
    var defaultBranch: String = "main",
    // --- Индексация ---
    @Column(name = "last_commit_sha")
    var lastCommitSha: String? = null,
    @Column(name = "last_indexed_at")
    var lastIndexedAt: OffsetDateTime? = null,
    @Column(name = "last_index_status")
    var lastIndexStatus: String? = null, // success|failed|partial|running
    @Column(name = "last_index_error", columnDefinition = "text")
    var lastIndexError: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingest_cursor", columnDefinition = "jsonb", nullable = false)
    var ingestCursor: Map<String, Any> = emptyMap(),
    // --- Организация / Категоризация ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "owners", columnDefinition = "jsonb", nullable = false)
    var owners: List<OwnerRef> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contacts", columnDefinition = "jsonb", nullable = false)
    var contacts: List<ContactRef> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]", nullable = false)
    var tags: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages", columnDefinition = "text[]", nullable = false)
    var languages: List<String> = emptyList(),
    // --- Настройки RAG / Embedding ---
    @Column(name = "embedding_model")
    var embeddingModel: String? = null,
    @Column(name = "embedding_dim", nullable = false)
    var embeddingDim: Int = 1024,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ann_index_params", columnDefinition = "jsonb", nullable = false)
    var annIndexParams: AnnIndexParams = AnnIndexParams(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rag_prefs", columnDefinition = "jsonb", nullable = false)
    var ragPrefs: RagPrefs = RagPrefs(),
    // --- Политики / Ретенция ---
    @Column(name = "retention_days")
    var retentionDays: Int? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pii_policy", columnDefinition = "jsonb", nullable = false)
    var piiPolicy: PiiPolicy = PiiPolicy(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    var metadata: Map<String, Any> = emptyMap(),
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
