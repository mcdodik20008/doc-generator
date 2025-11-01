package com.bftcom.docgenerator.api.graph.dto

data class GEdge(
    val id: String, // edgeId (или "src->dst:kind")
    val source: String,
    val target: String,
    val kind: String, // EdgeKind (CALLS/READS/WRITES/RELATES/...)
    val weight: Double? = null,
)
