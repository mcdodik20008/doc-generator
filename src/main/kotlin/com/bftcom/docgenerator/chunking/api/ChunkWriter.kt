package com.bftcom.docgenerator.chunking.api

import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan

interface ChunkWriter {
    fun savePlan(plans: List<ChunkPlan>): SaveResult

    data class SaveResult(
        val written: Long,
        val skipped: Long,
    )
}
