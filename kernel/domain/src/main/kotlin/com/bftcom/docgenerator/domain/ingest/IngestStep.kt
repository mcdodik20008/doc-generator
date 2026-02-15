package com.bftcom.docgenerator.domain.ingest

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
@Table(name = "ingest_step", schema = "doc_generator")
class IngestStep(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    var run: IngestRun,

    @Column(name = "step_type", nullable = false)
    var stepType: String,

    @Column(nullable = false)
    var status: String = IngestStepStatus.PENDING.name,

    @Column(name = "items_processed")
    var itemsProcessed: Int? = null,

    @Column(name = "items_total")
    var itemsTotal: Int? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: OffsetDateTime? = null,

    @Column(name = "finished_at")
    var finishedAt: OffsetDateTime? = null,
)
