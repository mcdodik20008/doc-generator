package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.ingest.IngestRun
import org.springframework.data.jpa.repository.JpaRepository

interface IngestRunRepository : JpaRepository<IngestRun, Long> {
    fun findByApplicationIdOrderByCreatedAtDesc(applicationId: Long): List<IngestRun>

    fun findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
        applicationId: Long,
        status: String,
    ): IngestRun?
}
