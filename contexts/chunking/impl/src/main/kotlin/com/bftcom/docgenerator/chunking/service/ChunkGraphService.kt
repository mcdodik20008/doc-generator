package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.db.ChunkGraphRepository
import com.bftcom.docgenerator.domain.dto.GraphResponse
import org.springframework.stereotype.Service

@Service
class ChunkGraphService(
    private val repo: ChunkGraphRepository,
) {
    fun buildGraph(
        appId: Long,
        kinds: Set<String>,
        limit: Int,
        withRelations: Boolean,
    ): GraphResponse {
        val nodes = repo.loadNodes(appId, kinds, limit)
        val edges = repo.loadEdges(appId, nodes.map { it.id }.toSet(), withRelations)
        return GraphResponse(nodes, edges)
    }

    fun expandNode(
        nodeId: String,
        limit: Int,
    ): GraphResponse {
        val nodes = repo.loadNeighbors(nodeId, limit)
        val edges = repo.loadEdgesByNode(nodeId, nodes.map { it.id }.toSet())
        return GraphResponse(nodes, edges)
    }
}
