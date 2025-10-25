package com.bftcom.docgenerator.chunking

import com.bftcom.docgenerator.chunking.model.ChunkPlan
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.node.Node

interface ChunkStrategy {
    /**
     * На вход — Node + связанные исходящие рёбра (для enrichment).
     * На выход — список планов на запись чанков (без сайд-эффектов).
     */
    fun buildChunks(node: Node, edges: List<Edge>): List<ChunkPlan>
}