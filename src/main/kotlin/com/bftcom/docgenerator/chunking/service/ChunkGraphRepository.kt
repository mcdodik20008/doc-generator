package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.api.graph.dto.GEdge
import com.bftcom.docgenerator.api.graph.dto.GNode

interface ChunkGraphRepository {
    fun loadNodes(
        appId: Long,
        kinds: Set<String>,
        limit: Int,
    ): List<GNode>

    fun loadEdges(
        appId: Long,
        nodeIds: Set<String>,
        withRelations: Boolean,
    ): List<GEdge>

    fun loadNeighbors(
        nodeId: String,
        limit: Int,
    ): List<GNode>

    fun loadEdgesByNode(
        nodeId: String,
        neighborIds: Set<String>,
    ): List<GEdge>
}
