package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NeighborhoodExpansionAdvisorTest {

    @Test
    fun `process - пропускает если NEIGHBOR_NODES уже есть`() {
        val edgeRepo = mockk<EdgeRepository>(relaxed = true)
        val nodeRepo = mockk<NodeRepository>(relaxed = true)
        val advisor = NeighborhoodExpansionAdvisor(edgeRepo, nodeRepo)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).apply {
            setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, listOf(mockk<Node>()))
        }

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { edgeRepo.findAllBySrcIdIn(any<Set<Long>>()) }
        verify(exactly = 0) { nodeRepo.findAllByIdIn(any()) }
    }

    @Test
    fun `process - пропускает если EXACT_NODES отсутствуют`() {
        val edgeRepo = mockk<EdgeRepository>(relaxed = true)
        val nodeRepo = mockk<NodeRepository>(relaxed = true)
        val advisor = NeighborhoodExpansionAdvisor(edgeRepo, nodeRepo)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { edgeRepo.findAllBySrcIdIn(any<Set<Long>>()) }
        verify(exactly = 0) { nodeRepo.findAllByIdIn(any()) }
    }

    @Test
    fun `process - находит соседей по важным рёбрам и пишет метаданные`() {
        val edgeRepo = mockk<EdgeRepository>()
        val nodeRepo = mockk<NodeRepository>()
        val advisor = NeighborhoodExpansionAdvisor(edgeRepo, nodeRepo)

        val app = Application(id = 1L, key = "app", name = "App")
        val seed = Node(
            id = 100L,
            application = app,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        val neighbor = Node(
            id = 200L,
            application = app,
            fqn = "com.example.UserRepository.find",
            name = "find",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )

        val importantEdge = Edge(src = seed, dst = neighbor, kind = EdgeKind.CALLS_CODE)
        val unimportantEdge = Edge(src = seed, dst = neighbor, kind = EdgeKind.CONTAINS)

        every { edgeRepo.findAllBySrcIdIn(setOf(100L)) } returns listOf(importantEdge, unimportantEdge)
        every { edgeRepo.findAllByDstIdIn(setOf(100L)) } returns emptyList()
        every { nodeRepo.findAllByIdIn(setOf(200L)) } returns listOf(neighbor)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).apply {
            setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(seed))
        }

        val result = advisor.process(context)

        assertThat(result).isTrue
        val neighbors = context.getMetadata<List<Node>>(QueryMetadataKeys.NEIGHBOR_NODES)
        assertThat(neighbors).isNotNull
        assertThat(neighbors!!).hasSize(1)
        assertThat(neighbors[0].id).isEqualTo(200L)
        assertThat(context.getMetadata<Int>(QueryMetadataKeys.NEIGHBOR_EXPANSION_RADIUS)).isEqualTo(1)
        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("NeighborhoodExpansion")
    }

    @Test
    fun `process - не падает если репозитории кидают исключение`() {
        val edgeRepo = mockk<EdgeRepository>()
        val nodeRepo = mockk<NodeRepository>(relaxed = true)
        val advisor = NeighborhoodExpansionAdvisor(edgeRepo, nodeRepo)

        val app = Application(id = 1L, key = "app", name = "App")
        val seed = Node(
            id = 100L,
            application = app,
            fqn = "com.example.UserService",
            name = "UserService",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )

        every { edgeRepo.findAllBySrcIdIn(any<Set<Long>>()) } throws RuntimeException("db down")

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).apply {
            setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(seed))
        }

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.hasMetadata(QueryMetadataKeys.NEIGHBOR_NODES)).isFalse
    }
}

