package com.bftcom.docgenerator.graph.model

import java.time.OffsetDateTime

data class BuildResult(
    val nodes: Int,
    val edges: Int,
    val startedAt: OffsetDateTime = OffsetDateTime.now(),
    val finishedAt: OffsetDateTime = OffsetDateTime.now(),
)
