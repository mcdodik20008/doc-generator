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
import org.springframework.transaction.annotation.Transactional

class EdgeRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var edgeRepository: EdgeRepository

    @Autowired
    private lateinit var nodeRepository: NodeRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    private lateinit var application: Application
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node

    @BeforeEach
    fun setUp() {
        application = Application(
            key = "test-app-${System.currentTimeMillis()}",
            name = "Test Application",
        )
        application = applicationRepository.save(application)

        node1 = Node(
            application = application,
            fqn = "com.example.Class1",
            name = "Class1",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node1 = nodeRepository.save(node1)

        node2 = Node(
            application = application,
            fqn = "com.example.Class2",
            name = "Class2",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node2 = nodeRepository.save(node2)

        node3 = Node(
            application = application,
            fqn = "com.example.Class3",
            name = "Class3",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node3 = nodeRepository.save(node3)
    }

    @Test
    fun `findAllBySrcId - возвращает исходящие рёбра`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node1,
            dst = node3,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllBySrcId(node1.id!!)

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.src.id }).containsOnly(node1.id)
        assertThat(edges.map { it.dst.id }).containsExactlyInAnyOrder(node2.id, node3.id)
    }

    @Test
    fun `findAllByDstId - возвращает входящие рёбра`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node3,
            dst = node2,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllByDstId(node2.id!!)

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.dst.id }).containsOnly(node2.id)
        assertThat(edges.map { it.src.id }).containsExactlyInAnyOrder(node1.id, node3.id)
    }

    @Test
    fun `findAllBySrcIdIn - возвращает рёбра для множества источников`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node2,
            dst = node3,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllBySrcIdIn(setOf(node1.id!!, node2.id!!))

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.src.id }).containsExactlyInAnyOrder(node1.id, node2.id)
    }

    @Test
    fun `findAllByDstIdIn - возвращает рёбра для множества целей`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node3,
            dst = node2,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllByDstIdIn(setOf(node2.id!!))

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.dst.id }).containsOnly(node2.id)
    }

    @Test
    fun `findAllBySrcIdAndDstIdIn - возвращает рёбра от источника к множеству целей`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node1,
            dst = node3,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllBySrcIdAndDstIdIn(node1.id!!, setOf(node2.id!!, node3.id!!))

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.src.id }).containsOnly(node1.id)
        assertThat(edges.map { it.dst.id }).containsExactlyInAnyOrder(node2.id, node3.id)
    }

    @Test
    fun `findAllByDstIdAndSrcIdIn - возвращает рёбра к цели от множества источников`() {
        // Given
        val edge1 = Edge(
            src = node1,
            dst = node2,
            kind = EdgeKind.DEPENDS_ON,
            evidence = emptyMap(),
        )
        val edge2 = Edge(
            src = node3,
            dst = node2,
            kind = EdgeKind.CALLS_CODE,
            evidence = emptyMap(),
        )
        edgeRepository.save(edge1)
        edgeRepository.save(edge2)

        // When
        val edges = edgeRepository.findAllByDstIdAndSrcIdIn(node2.id!!, setOf(node1.id!!, node3.id!!))

        // Then
        assertThat(edges).hasSize(2)
        assertThat(edges.map { it.dst.id }).containsOnly(node2.id)
        assertThat(edges.map { it.src.id }).containsExactlyInAnyOrder(node1.id, node3.id)
    }

    @Test
    fun `upsert - создаёт новое ребро`() {
        // When
        edgeRepository.upsert(
            srcId = node1.id!!,
            dstId = node2.id!!,
            kind = "DEPENDS_ON",
        )
        edgeRepository.flush()

        // Then
        val edge = edgeRepository.findAll().find { it.src.id == node1.id && it.dst.id == node2.id }
        assertThat(edge).isNotNull
        assertThat(edge!!.kind).isEqualTo(EdgeKind.DEPENDS_ON)
    }

    @Test
    fun `upsert - не создаёт дубликат при повторном вызове`() {
        // Given: создаём первое ребро
        edgeRepository.upsert(
            srcId = node1.id!!,
            dstId = node2.id!!,
            kind = "DEPENDS_ON",
        )
        edgeRepository.flush()

        val initialCount = edgeRepository.count()

        // When: пытаемся создать то же ребро
        edgeRepository.upsert(
            srcId = node1.id!!,
            dstId = node2.id!!,
            kind = "DEPENDS_ON",
        )
        edgeRepository.flush()

        // Then: количество не изменилось
        assertThat(edgeRepository.count()).isEqualTo(initialCount)
    }
}