package com.bftcom.docgenerator.api.graph.dto

// Graph DTO
data class GraphResponse(
    val nodes: List<GNode>,
    val edges: List<GEdge>,
)
