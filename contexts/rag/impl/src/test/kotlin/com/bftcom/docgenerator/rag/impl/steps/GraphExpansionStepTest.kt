package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphExpansionStepTest {
    private val edgeRepository = mockk<EdgeRepository>()
    private val nodeRepository = mockk<NodeRepository>()
    private val step = GraphExpansionStep(edgeRepository, nodeRepository)

    @Test
    fun `execute - находит соседей и формирует текст связей`() {
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

        val edge = Edge(src = seed, dst = neighbor, kind = EdgeKind.CALLS_CODE)

        every { edgeRepository.findAllBySrcIdIn(setOf(100L)) } returns listOf(edge)
        every { edgeRepository.findAllByDstIdIn(setOf(100L)) } returns emptyList()
        every { nodeRepository.findAllByIdIn(setOf(200L)) } returns listOf(neighbor)

        val context = QueryProcessingContext(
            originalQuery = "getUser",
            currentQuery = "getUser",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(seed))

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val graphText = result.context.getMetadata<String>(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)
        assertThat(graphText).isNotNull
        assertThat(graphText!!).contains("CALLS_CODE")
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - пропускает если нет EXACT_NODES`() {
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("NO_NODES")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)).isFalse
    }

    @Test
    fun `execute - фильтрует только важные типы ребер`() {
        val app = Application(id = 1L, key = "app", name = "App")
        val seed = Node(
            id = 100L,
            application = app,
            fqn = "com.example.UserService",
            name = "UserService",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )

        val importantEdge = Edge(src = seed, dst = seed, kind = EdgeKind.CALLS_CODE)
        val unimportantEdge = Edge(src = seed, dst = seed, kind = EdgeKind.CONTAINS)

        every { edgeRepository.findAllBySrcIdIn(setOf(100L)) } returns listOf(importantEdge, unimportantEdge)
        every { edgeRepository.findAllByDstIdIn(setOf(100L)) } returns emptyList()
        every { nodeRepository.findAllByIdIn(any()) } returns emptyList()

        val context = QueryProcessingContext(
            originalQuery = "UserService",
            currentQuery = "UserService",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(seed))

        val result = step.execute(context)

        val graphText = result.context.getMetadata<String>(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)
        // Должен содержать только CALLS_CODE, не CONTAINS
        assertThat(graphText).doesNotContain("CONTAINS")
    }
}
