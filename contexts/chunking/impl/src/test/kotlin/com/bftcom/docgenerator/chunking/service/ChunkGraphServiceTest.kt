package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.db.ChunkGraphRepository
import com.bftcom.docgenerator.domain.dto.GEdge
import com.bftcom.docgenerator.domain.dto.GNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkGraphServiceTest {
    @Test
    fun `buildGraph - грузит ноды и рёбра и возвращает GraphResponse`() {
        val repo = mockk<ChunkGraphRepository>()
        val service = ChunkGraphService(repo)

        val nodes = listOf(GNode(id = "n1", label = "N1", kind = "CLASS", group = null))
        val edges = listOf(GEdge(id = "e1", source = "n1", target = "n2", kind = "CALLS"))

        every { repo.loadNodes(appId = 1L, kinds = setOf("CLASS"), limit = 10) } returns nodes
        every { repo.loadEdges(appId = 1L, nodeIds = setOf("n1"), withRelations = true) } returns edges

        val res = service.buildGraph(appId = 1L, kinds = setOf("CLASS"), limit = 10, withRelations = true)

        assertEquals(nodes, res.nodes)
        assertEquals(edges, res.edges)
        verify(exactly = 1) { repo.loadNodes(1L, setOf("CLASS"), 10) }
        verify(exactly = 1) { repo.loadEdges(1L, setOf("n1"), true) }
    }

    @Test
    fun `expandNode - грузит соседей и рёбра по ноде`() {
        val repo = mockk<ChunkGraphRepository>()
        val service = ChunkGraphService(repo)

        val nodes = listOf(GNode(id = "n2", label = "N2", kind = "METHOD", group = null))
        val edges = listOf(GEdge(id = "e1", source = "n1", target = "n2", kind = "CALLS"))

        every { repo.loadNeighbors(nodeId = "n1", limit = 5) } returns nodes
        every { repo.loadEdgesByNode(nodeId = "n1", neighborIds = setOf("n2")) } returns edges

        val res = service.expandNode(nodeId = "n1", limit = 5)

        assertEquals(nodes, res.nodes)
        assertEquals(edges, res.edges)
        verify(exactly = 1) { repo.loadNeighbors("n1", 5) }
        verify(exactly = 1) { repo.loadEdgesByNode("n1", setOf("n2")) }
    }
}

