package com.bftcom.docgenerator.api.audit

import com.bftcom.docgenerator.db.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

data class AuditLogResponse(
    val id: Long,
    val userId: String?,
    val action: String,
    val resource: String,
    val httpMethod: String?,
    val ipAddress: String?,
    val createdAt: OffsetDateTime,
)

data class AuditLogSearchResult(
    val content: List<AuditLogResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)

@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository,
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) user: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) resource: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): AuditLogSearchResult {
        val pageable = PageRequest.of(page, size.coerceIn(1, 200))
        val result = auditLogRepository.search(user, action, resource, from, to, pageable)

        return AuditLogSearchResult(
            content = result.content.map {
                AuditLogResponse(
                    id = it.id!!,
                    userId = it.userId,
                    action = it.action,
                    resource = it.resource,
                    httpMethod = it.httpMethod,
                    ipAddress = it.ipAddress,
                    createdAt = it.createdAt,
                )
            },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = page,
            size = size,
        )
    }
}
