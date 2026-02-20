package com.bftcom.docgenerator.analysis

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ChangeImpactServiceTest {

    private val edgeRepository: EdgeRepository = mock()
    private val nodeRepository: NodeRepository = mock()
    private val changeImpactService = ChangeImpactService(edgeRepository, nodeRepository)

    // Helper to create mocked nodes
    private fun createNode(id: Long, fqn: String): Node {
        val app =
                Application(
                        id = 1L,
                        key = "test-app",
                        name = "test-app",
                        defaultBranch = "main",
                        repoUrl = "http"
                )
        return Node(
                id = id,
                application = app,
                fqn = fqn,
                name = fqn.substringAfterLast("."),
                kind = NodeKind.CLASS,
                lang = Lang.kotlin
        )
    }

    @Test
    fun `test analyzeImpact linear dependency A depends on B depends on C`() {
        // C is changed (id=3). B (id=2) depends on C. A (id=1) depends on B.
        val nodeA = createNode(1, "A")
        val nodeB = createNode(2, "B")
        val nodeC = createNode(3, "C")

        val edgeB_C = Edge(src = nodeB, dst = nodeC, kind = EdgeKind.CALLS)
        val edgeA_B = Edge(src = nodeA, dst = nodeB, kind = EdgeKind.CALLS)

        `when`(nodeRepository.findById(3L)).thenReturn(Optional.of(nodeC))
        `when`(edgeRepository.findAllByDstIdIn(setOf(3L))).thenReturn(listOf(edgeB_C))
        `when`(edgeRepository.findAllByDstIdIn(setOf(2L))).thenReturn(listOf(edgeA_B))
        `when`(edgeRepository.findAllByDstIdIn(setOf(1L))).thenReturn(emptyList())

        `when`(nodeRepository.findAllByIdIn(setOf(2L))).thenReturn(listOf(nodeB))
        `when`(nodeRepository.findAllByIdIn(setOf(1L))).thenReturn(listOf(nodeA))

        val result = changeImpactService.analyzeImpact(3L, maxDepth = 5)

        assertEquals(3L, result.rootNodeId)
        assertEquals(2, result.totalImpactedNodes)
        assertEquals(2, result.maxDepthReached)

        // Depth 1 should be B
        assertEquals(1, result.impactedNodes.find { it.id == 2L }?.depth)
        // Depth 2 should be A
        assertEquals(2, result.impactedNodes.find { it.id == 1L }?.depth)
    }
}
