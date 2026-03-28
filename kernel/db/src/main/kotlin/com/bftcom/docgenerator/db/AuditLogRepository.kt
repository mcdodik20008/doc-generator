package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.audit.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByUserIdOrderByCreatedAtDesc(
        userId: String,
        pageable: Pageable,
    ): Page<AuditLog>

    @Query(
        """
        SELECT a FROM AuditLog a
        WHERE (:userId IS NULL OR a.userId = :userId)
          AND (:action IS NULL OR a.action = :action)
          AND (:resource IS NULL OR a.resource LIKE CONCAT('%', :resource, '%'))
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
    """,
    )
    fun search(
        userId: String?,
        action: String?,
        resource: String?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        pageable: Pageable,
    ): Page<AuditLog>
}
