package com.bftcom.docgenerator.domain.ingest

import com.bftcom.docgenerator.domain.application.Application
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "ingest_run", schema = "doc_generator")
class IngestRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    var application: Application,

    @Column(nullable = false)
    var status: String = IngestRunStatus.PENDING.name,

    @Column(name = "triggered_by")
    var triggeredBy: String? = null,

    var branch: String? = null,

    @Column(name = "commit_sha")
    var commitSha: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: OffsetDateTime? = null,

    @Column(name = "finished_at")
    var finishedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
