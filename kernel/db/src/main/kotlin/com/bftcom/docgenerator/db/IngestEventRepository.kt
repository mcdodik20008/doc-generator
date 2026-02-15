package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.ingest.IngestEvent
import org.springframework.data.jpa.repository.JpaRepository

interface IngestEventRepository : JpaRepository<IngestEvent, Long> {
    fun findByRunIdOrderByCreatedAtAsc(runId: Long): List<IngestEvent>

    fun findByRunIdAndIdGreaterThanOrderByCreatedAtAsc(runId: Long, afterId: Long): List<IngestEvent>
}
