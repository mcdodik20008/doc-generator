package com.bftcom.docgenerator.domain.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * Immutable audit log entry.
 * Records all mutation operations (POST, PUT, DELETE) on /api/ endpoints.
 */
@Entity
@Table(name = "audit_log", schema = "doc_generator")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** API key name or "anonymous" */
    @Column(name = "user_id")
    var userId: String? = null,

    /** Action type: CREATE, DELETE, INGEST_START, etc. */
    @Column(nullable = false)
    var action: String,

    /** Request path, e.g. "/api/v1/ingest/start/5" */
    @Column(nullable = false)
    var resource: String,

    @Column(name = "http_method")
    var httpMethod: String? = null,

    @Column(name = "request_body", columnDefinition = "text")
    var requestBody: String? = null,

    @Column(name = "ip_address")
    var ipAddress: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)

