package com.bftcom.docgenerator.graph.api.linker.metrics

import com.bftcom.docgenerator.domain.enums.EdgeKind

interface LinkerMetrics {
    fun record(
        edgeKind: EdgeKind,
        count: Int,
    )

    fun recordPhase(
        phase: String,
        durationMs: Long,
    )
}
