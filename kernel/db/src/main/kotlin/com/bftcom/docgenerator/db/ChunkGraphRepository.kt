package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.dto.GEdge
import com.bftcom.docgenerator.domain.dto.GNode

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
