package com.bftcom.docgenerator.graph.api.dto

// Graph DTO
data class GraphResponse(
    val nodes: List<GNode>,
    val edges: List<GEdge>,
)
