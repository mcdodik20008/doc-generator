package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.db.ChunkGraphRepository
import com.bftcom.docgenerator.domain.dto.GEdge
import com.bftcom.docgenerator.domain.dto.GNode
import com.bftcom.docgenerator.domain.dto.GraphResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkGraphServiceTest {
    private lateinit var repo: ChunkGraphRepository
    private lateinit var service: ChunkGraphService

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        service = ChunkGraphService(repo)
    }

    @Test
    fun `buildGraph - возвращает GraphResponse с nodes и edges`() {
        // given
        val nodes = listOf(
            GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"),
            GNode(id = "200", label = "Node2", kind = "METHOD", group = "com.example"),
        )
        val edges = listOf(
            GEdge(id = "1", source = "100", target = "200", kind = "CALLS"),
        )

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(1L, setOf("100", "200"), true) } returns edges

        // when
        val result = service.buildGraph(1L, emptySet(), 500, true)

        // then
        assertThat(result).isNotNull
        assertThat(result.nodes).hasSize(2)
        assertThat(result.edges).hasSize(1)
        assertThat(result.nodes[0].id).isEqualTo("100")
        assertThat(result.edges[0].source).isEqualTo("100")
        assertThat(result.edges[0].target).isEqualTo("200")
    }

    @Test
    fun `buildGraph - вызывает repo loadNodes с корректными параметрами`() {
        // given
        val kinds = setOf("CLASS", "METHOD")
        val limit = 100
        val nodes = emptyList<GNode>()

        every { repo.loadNodes(1L, kinds, limit) } returns nodes
        every { repo.loadEdges(any(), any(), any()) } returns emptyList()

        // when
        service.buildGraph(1L, kinds, limit, true)

        // then
        verify(exactly = 1) { repo.loadNodes(1L, kinds, limit) }
    }

    @Test
    fun `buildGraph - вызывает repo loadEdges с корректными nodeIds и withRelations`() {
        // given
        val nodes = listOf(
            GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"),
            GNode(id = "200", label = "Node2", kind = "METHOD", group = "com.example"),
        )

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(1L, setOf("100", "200"), true) } returns emptyList()

        // when
        service.buildGraph(1L, emptySet(), 500, true)

        // then
        verify(exactly = 1) { repo.loadEdges(1L, setOf("100", "200"), true) }
    }

    @Test
    fun `buildGraph - обрабатывает пустой набор kinds`() {
        // given
        val nodes = listOf(GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"))

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(any(), any(), any()) } returns emptyList()

        // when
        val result = service.buildGraph(1L, emptySet(), 500, true)

        // then
        assertThat(result.nodes).hasSize(1)
        verify(exactly = 1) { repo.loadNodes(1L, emptySet(), 500) }
    }

    @Test
    fun `buildGraph - обрабатывает limit = 0 (пустой результат)`() {
        // given
        val nodes = emptyList<GNode>()

        every { repo.loadNodes(1L, emptySet(), 0) } returns nodes
        every { repo.loadEdges(any(), any(), any()) } returns emptyList()

        // when
        val result = service.buildGraph(1L, emptySet(), 0, true)

        // then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        verify(exactly = 1) { repo.loadNodes(1L, emptySet(), 0) }
        verify(exactly = 1) { repo.loadEdges(1L, emptySet(), true) }
    }

    @Test
    fun `buildGraph - обрабатывает withRelations = false`() {
        // given
        val nodes = listOf(GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"))

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(1L, setOf("100"), false) } returns emptyList()

        // when
        val result = service.buildGraph(1L, emptySet(), 500, false)

        // then
        assertThat(result.nodes).hasSize(1)
        verify(exactly = 1) { repo.loadEdges(1L, setOf("100"), false) }
    }

    @Test
    fun `buildGraph - обрабатывает пустой список nodes (edges тоже пустой)`() {
        // given
        val nodes = emptyList<GNode>()

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(1L, emptySet(), true) } returns emptyList()

        // when
        val result = service.buildGraph(1L, emptySet(), 500, true)

        // then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        verify(exactly = 1) { repo.loadEdges(1L, emptySet(), true) }
    }

    @Test
    fun `buildGraph - обрабатывает большой limit (проверка производительности)`() {
        // given
        val largeLimit = 10000
        val nodes = (1..1000).map { GNode(id = "$it", label = "Node$it", kind = "CLASS", group = "com.example") }

        every { repo.loadNodes(1L, emptySet(), largeLimit) } returns nodes
        every { repo.loadEdges(any(), any(), any()) } returns emptyList()

        // when
        val result = service.buildGraph(1L, emptySet(), largeLimit, true)

        // then
        assertThat(result.nodes).hasSize(1000)
        verify(exactly = 1) { repo.loadNodes(1L, emptySet(), largeLimit) }
    }

    @Test
    fun `buildGraph - корректно маппит nodeIds из nodes для loadEdges`() {
        // given
        val nodes = listOf(
            GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"),
            GNode(id = "200", label = "Node2", kind = "METHOD", group = "com.example"),
            GNode(id = "300", label = "Node3", kind = "FIELD", group = "com.example"),
        )

        every { repo.loadNodes(1L, emptySet(), 500) } returns nodes
        every { repo.loadEdges(1L, setOf("100", "200", "300"), true) } returns emptyList()

        // when
        service.buildGraph(1L, emptySet(), 500, true)

        // then
        verify(exactly = 1) { repo.loadEdges(1L, setOf("100", "200", "300"), true) }
    }

    @Test
    fun `expandNode - возвращает GraphResponse для соседних узлов`() {
        // given
        val nodeId = "100"
        val nodes = listOf(
            GNode(id = "200", label = "Neighbor1", kind = "CLASS", group = "com.example"),
            GNode(id = "300", label = "Neighbor2", kind = "METHOD", group = "com.example"),
        )
        val edges = listOf(
            GEdge(id = "1", source = "100", target = "200", kind = "CALLS"),
            GEdge(id = "2", source = "100", target = "300", kind = "READS"),
        )

        every { repo.loadNeighbors(nodeId, 200) } returns nodes
        every { repo.loadEdgesByNode(nodeId, setOf("200", "300")) } returns edges

        // when
        val result = service.expandNode(nodeId, 200)

        // then
        assertThat(result).isNotNull
        assertThat(result.nodes).hasSize(2)
        assertThat(result.edges).hasSize(2)
        assertThat(result.nodes[0].id).isEqualTo("200")
    }

    @Test
    fun `expandNode - вызывает repo loadNeighbors с корректными параметрами`() {
        // given
        val nodeId = "100"
        val limit = 50
        val nodes = emptyList<GNode>()

        every { repo.loadNeighbors(nodeId, limit) } returns nodes
        every { repo.loadEdgesByNode(any(), any()) } returns emptyList()

        // when
        service.expandNode(nodeId, limit)

        // then
        verify(exactly = 1) { repo.loadNeighbors(nodeId, limit) }
    }

    @Test
    fun `expandNode - вызывает repo loadEdgesByNode с корректными nodeIds`() {
        // given
        val nodeId = "100"
        val nodes = listOf(
            GNode(id = "200", label = "Neighbor1", kind = "CLASS", group = "com.example"),
            GNode(id = "300", label = "Neighbor2", kind = "METHOD", group = "com.example"),
        )

        every { repo.loadNeighbors(nodeId, 200) } returns nodes
        every { repo.loadEdgesByNode(nodeId, setOf("200", "300")) } returns emptyList()

        // when
        service.expandNode(nodeId, 200)

        // then
        verify(exactly = 1) { repo.loadEdgesByNode(nodeId, setOf("200", "300")) }
    }

    @Test
    fun `expandNode - обрабатывает несуществующий nodeId (пустой результат)`() {
        // given
        val nodeId = "999"
        val nodes = emptyList<GNode>()

        every { repo.loadNeighbors(nodeId, 200) } returns nodes
        every { repo.loadEdgesByNode(any(), any()) } returns emptyList()

        // when
        val result = service.expandNode(nodeId, 200)

        // then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        verify(exactly = 1) { repo.loadNeighbors(nodeId, 200) }
        verify(exactly = 1) { repo.loadEdgesByNode(nodeId, emptySet()) }
    }

    @Test
    fun `expandNode - обрабатывает limit = 0`() {
        // given
        val nodeId = "100"
        val nodes = emptyList<GNode>()

        every { repo.loadNeighbors(nodeId, 0) } returns nodes
        every { repo.loadEdgesByNode(any(), any()) } returns emptyList()

        // when
        val result = service.expandNode(nodeId, 0)

        // then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        verify(exactly = 1) { repo.loadNeighbors(nodeId, 0) }
    }

    @Test
    fun `expandNode - обрабатывает узел без соседей`() {
        // given
        val nodeId = "100"
        val nodes = emptyList<GNode>()

        every { repo.loadNeighbors(nodeId, 200) } returns nodes
        every { repo.loadEdgesByNode(nodeId, emptySet()) } returns emptyList()

        // when
        val result = service.expandNode(nodeId, 200)

        // then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        verify(exactly = 1) { repo.loadEdgesByNode(nodeId, emptySet()) }
    }

    @Test
    fun `expandNode - корректно маппит nodeIds из neighbors для loadEdgesByNode`() {
        // given
        val nodeId = "100"
        val nodes = listOf(
            GNode(id = "200", label = "Neighbor1", kind = "CLASS", group = "com.example"),
            GNode(id = "300", label = "Neighbor2", kind = "METHOD", group = "com.example"),
            GNode(id = "400", label = "Neighbor3", kind = "FIELD", group = "com.example"),
        )

        every { repo.loadNeighbors(nodeId, 200) } returns nodes
        every { repo.loadEdgesByNode(nodeId, setOf("200", "300", "400")) } returns emptyList()

        // when
        service.expandNode(nodeId, 200)

        // then
        verify(exactly = 1) { repo.loadEdgesByNode(nodeId, setOf("200", "300", "400")) }
    }

    @Test
    fun `buildGraph - обрабатывает kinds с несколькими значениями`() {
        // given
        val kinds = setOf("CLASS", "METHOD", "FIELD")
        val nodes = listOf(
            GNode(id = "100", label = "Node1", kind = "CLASS", group = "com.example"),
        )

        every { repo.loadNodes(1L, kinds, 500) } returns nodes
        every { repo.loadEdges(any(), any(), any()) } returns emptyList()

        // when
        val result = service.buildGraph(1L, kinds, 500, true)

        // then
        assertThat(result.nodes).hasSize(1)
        verify(exactly = 1) { repo.loadNodes(1L, kinds, 500) }
    }
}
