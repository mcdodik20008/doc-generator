package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.ingest.IngestStep
import org.springframework.data.jpa.repository.JpaRepository

interface IngestStepRepository : JpaRepository<IngestStep, Long> {
    fun findByRunIdOrderByIdAsc(runId: Long): List<IngestStep>
}
