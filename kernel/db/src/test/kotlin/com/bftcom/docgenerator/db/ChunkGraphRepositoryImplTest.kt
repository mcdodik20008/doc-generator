package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ChunkGraphRepositoryImpl::class)
class ChunkGraphRepositoryImplTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var chunkGraphRepository: ChunkGraphRepository

    @Autowired
    private lateinit var nodeRepository: NodeRepository

    @Autowired
    private lateinit var edgeRepository: EdgeRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    private lateinit var application: Application
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node
    private lateinit var node4: Node

    @BeforeEach
    fun setUp() {
        // Given: Создаём Application
        application = Application(
            key = "test-app-${System.currentTimeMillis()}",
            name = "Test Application",
        )
        application = applicationRepository.save(application)

        // Given: Создаём несколько Node разных типов
        node1 = Node(
            application = application,
            fqn = "com.example.TestClass1",
            name = "TestClass1",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node1 = nodeRepository.save(node1)

        node2 = Node(
            application = application,
            fqn = "com.example.TestClass2",
            name = "TestClass2",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node2 = nodeRepository.save(node2)

        node3 = Node(
            application = application,
            fqn = "com.example.testMethod1",
            name = "testMethod1",
            packageName = "com.example",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = node1,
        )
        node3 = nodeRepository.save(node3)

        node4 = Node(
            application = application,
            fqn = "com.example.testMethod2",
            name = "testMethod2",
            packageName = "com.example",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = node2,
        )
        node4 = nodeRepository.save(node4)
    }

    @Test
    fun `loadNodes - возвращает все узлы для application при пустом наборе kinds`() {
        // When
        val nodes = chunkGraphRepository.loadNodes(
            appId = application.id!!,
            kinds = emptySet(),
            limit = 100,
        )

        // Then
        assertThat(nodes).hasSize(4)
        assertThat(nodes.map { it.id }).containsExactlyInAnyOrder(
            node1.id.toString(),
            node2.id.toString(),
            node3.id.toString(),
            node4.id.toString(),
        )
    }

    @Test
    fun `loadNodes - фильтрует по kinds`() {
        // When
        val nodes = chunkGraphRepository.loadNodes(
            appId = application.id!!,
            kinds = setOf("CLASS"),
            limit = 100,
        )

        // Then
        assertThat(nodes).hasSize(2)
        assertThat(nodes.map { it.kind }).containsOnly("CLASS")
        assertThat(nodes.map { it.id }).containsExactlyInAnyOrder(
            node1.id.toString(),
            node2.id.toString(),
        )
    }

    @Test
    fun `loadNodes - уважает limit`() {
        // When
        val nodes = chunkGraphRepository.loadNodes(
            appId = application.id!!,
            kinds = emptySet(),
            limit = 2,
        )

        // Then
        assertThat(nodes).hasSizeLessThanOrEqualTo(2)
    }

    @Test
    fun `loadNodes - корректно маппит в GNode`() {
        // When
        val nodes = chunkGraphRepository.loadNodes(
            appId = application.id!!,
            kinds = setOf("CLASS"),
            limit = 100,
        )

        // Then
        assertThat(nodes).isNotEmpty
        val gNode = nodes.find { it.id == node1.id.toString() }
        assertThat(gNode).isNotNull
        assertThat(gNode!!.id).isEqualTo(node1.id.toString())
        assertThat(gNode.label).isEqualTo("TestClass1")
        assertThat(gNode.kind).isEqualTo("CLASS")
        assertThat(gNode.group).isEqualTo("com.example")
        assertThat(gNode.size).isNull()
        assertThat(gNode.color).isNull()
        assertThat(gNode.meta).isEmpty()
    }

    @Test
    fun `loadNodes - возвращает пустой список для несуществующего application`() {
        // When
        val nodes = chunkGraphRepository.loadNodes(
            appId = 99999L,
            kinds = emptySet(),
            limit = 100,
        )

        // Then
        assertThat(nodes).isEmpty()
    }

    @Test
    fun `loadEdges - возвращает рёбра для заданных nodeIds`() {
        // Given: создаём рёбра
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)

        val edge2 = Edge(
            src = node3,
            dst = node4,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge2)
        edgeRepository.flush()

        // When
        val edges = chunkGraphRepository.loadEdges(
            appId = application.id!!,
            nodeIds = setOf(node1.id.toString(), node2.id.toString()),
            withRelations = true,
        )

        // Then
        assertThat(edges).hasSize(1)
        assertThat(edges[0].source).isEqualTo(node1.id.toString())
        assertThat(edges[0].target).isEqualTo(node2.id.toString())
        assertThat(edges[0].kind).isEqualTo("DEPENDS_ON")
    }

    @Test
    fun `loadEdges - возвращает входящие и исходящие рёбра`() {
        // Given: создаём рёбра
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)

        val edge2 = Edge(
            src = node3,
            dst = node1, // входящее для node1
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge2)
        edgeRepository.flush()

        // When
        val edges = chunkGraphRepository.loadEdges(
            appId = application.id!!,
            nodeIds = setOf(node1.id.toString()),
            withRelations = true,
        )

        // Then: должны быть оба ребра (исходящее и входящее)
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.source }).containsExactlyInAnyOrder(
            node1.id.toString(),
            node3.id.toString(),
        )
        assertThat(edges.map { it.target }).containsExactlyInAnyOrder(
            node2.id.toString(),
            node1.id.toString(),
        )
    }

    @Test
    fun `loadEdges - корректно маппит в GEdge`() {
        // Given
        val edge = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge)
        edgeRepository.flush()

        // When
        val edges = chunkGraphRepository.loadEdges(
            appId = application.id!!,
            nodeIds = setOf(node1.id.toString(), node2.id.toString()),
            withRelations = true,
        )

        // Then
        assertThat(edges).hasSize(1)
        val gEdge = edges[0]
        assertThat(gEdge.id).isNotNull()
        assertThat(gEdge.source).isEqualTo(node1.id.toString())
        assertThat(gEdge.target).isEqualTo(node2.id.toString())
        assertThat(gEdge.kind).isEqualTo("DEPENDS_ON")
        assertThat(gEdge.weight).isNull()
    }

    @Test
    fun `loadEdges - возвращает пустой список для пустого набора nodeIds`() {
        // When
        val edges = chunkGraphRepository.loadEdges(
            appId = application.id!!,
            nodeIds = emptySet(),
            withRelations = true,
        )

        // Then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `loadNeighbors - возвращает соседей узла`() {
        // Given: создаём рёбра
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)

        val edge2 = Edge(
            src = node3,
            dst = node1,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge2)
        edgeRepository.flush()

        // When
        val neighbors = chunkGraphRepository.loadNeighbors(
            nodeId = node1.id.toString(),
            limit = 100,
        )

        // Then: должны быть node2 и node3
        assertThat(neighbors).hasSize(2)
        assertThat(neighbors.map { it.id }).containsExactlyInAnyOrder(
            node2.id.toString(),
            node3.id.toString(),
        )
    }

    @Test
    fun `loadNeighbors - уважает limit`() {
        // Given: создаём несколько соседей
        val node5 = Node(
            application = application,
            fqn = "com.example.Class3",
            name = "Class3",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val savedNode5 = nodeRepository.save(node5)

        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)

        val edge2 = Edge(
            src = node1,
            dst = savedNode5,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge2)
        edgeRepository.flush()

        // When
        val neighbors = chunkGraphRepository.loadNeighbors(
            nodeId = node1.id.toString(),
            limit = 1,
        )

        // Then
        assertThat(neighbors).hasSizeLessThanOrEqualTo(1)
    }

    @Test
    fun `loadNeighbors - возвращает пустой список для узла без соседей`() {
        // When
        val neighbors = chunkGraphRepository.loadNeighbors(
            nodeId = node1.id.toString(),
            limit = 100,
        )

        // Then
        assertThat(neighbors).isEmpty()
    }

    @Test
    fun `loadNeighbors - возвращает пустой список для несуществующего узла`() {
        // When
        val neighbors = chunkGraphRepository.loadNeighbors(
            nodeId = "99999",
            limit = 100,
        )

        // Then
        assertThat(neighbors).isEmpty()
    }

    @Test
    fun `loadEdgesByNode - возвращает рёбра между узлом и его соседями`() {
        // Given: создаём рёбра
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)

        val edge2 = Edge(
            src = node3,
            dst = node1,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge2)

        val node5 = Node(
            application = application,
            fqn = "com.example.Class5",
            name = "Class5",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val savedNode5 = nodeRepository.save(node5)

        val edge3 = Edge(
            src = node4,
            dst = savedNode5, // не относится к node1
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge3)
        edgeRepository.flush()

        // When
        val edges = chunkGraphRepository.loadEdgesByNode(
            nodeId = node1.id.toString(),
            neighborIds = setOf(node2.id.toString(), node3.id.toString()),
        )

        // Then: должны быть только рёбра, связывающие node1 с соседями
        assertThat(edges).hasSize(2)
        val sources = edges.map { it.source }.toSet()
        val targets = edges.map { it.target }.toSet()
        assertThat(sources + targets).containsExactlyInAnyOrder(
            node1.id.toString(),
            node2.id.toString(),
            node3.id.toString(),
        )
    }

    @Test
    fun `loadEdgesByNode - возвращает пустой список для узла без соседей`() {
        // When
        val edges = chunkGraphRepository.loadEdgesByNode(
            nodeId = node1.id.toString(),
            neighborIds = emptySet(),
        )

        // Then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `loadEdgesByNode - возвращает пустой список для несуществующего узла`() {
        // When
        val edges = chunkGraphRepository.loadEdgesByNode(
            nodeId = "99999",
            neighborIds = setOf(node2.id.toString()),
        )

        // Then
        assertThat(edges).isEmpty()
    }
}