package com.bftcom.docgenerator.api.dto.graph

// Graph DTO
data class GraphResponse(
    val nodes: List<GNode>,
    val edges: List<GEdge>,
)
