package com.bftcom.docgenerator.chunking.model.plan

data class ServiceMeta(
    val strategy: String = "per-node",
    val priority: Int = 0,
    val correlationId: String? = null,
    val traceId: String? = null,
)
