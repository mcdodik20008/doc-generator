package com.bftcom.docgenerator.domain.dto

// Graph DTO
data class GraphResponse(
    val nodes: List<GNode>,
    val edges: List<GEdge>,
)
