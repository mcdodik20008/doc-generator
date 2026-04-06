package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
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

class ArchitectureSynthesisStepTest {
    private val nodeRepository = mockk<NodeRepository>()
    private val edgeRepository = mockk<EdgeRepository>()
    private val step = ArchitectureSynthesisStep(nodeRepository, edgeRepository)

    private val app = Application(id = 1L, key = "test-app", name = "Test App")

    @Test
    fun `general architecture query collects node counts`() {
        val nodes =
            listOf(
                makeNode(1, NodeKind.ENDPOINT, "getUsers", meta = mapOf("httpMethod" to "GET", "path" to "/api/users")),
                makeNode(2, NodeKind.CLASS, "UserService"),
                makeNode(3, NodeKind.MODULE, "core"),
            )
        every { nodeRepository.findAllByApplicationId(1L, any()) } returns nodes
        every { nodeRepository.findAllByApplicationIdAndKindIn(1L, any(), any()) } returns emptyList()
        every { edgeRepository.findAllBySrcIdIn(any<Set<Long>>()) } returns emptyList()

        val context =
            makeContext("какая архитектура проекта?")
                .setMetadata(QueryMetadataKeys.APPLICATION_ID, 1L)

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val archText = result.context.getMetadata<String>(QueryMetadataKeys.ARCHITECTURE_CONTEXT_TEXT)
        assertThat(archText).isNotNull
        assertThat(archText).contains("Общая архитектура")
        assertThat(archText).contains("ENDPOINT: 1")
    }

    @Test
    fun `integration topic query focuses on clients and infra`() {
        val infraNode = makeNode(10, NodeKind.INFRASTRUCTURE, "ups-client", meta = mapOf("integrationType" to "http"))
        val clientNode = makeNode(11, NodeKind.CLIENT, "UpsClient")

        every { nodeRepository.findAllByApplicationId(1L, any()) } returns listOf(infraNode, clientNode)
        every { nodeRepository.findAllByApplicationIdAndKindIn(1L, match { NodeKind.CLIENT in it }, any()) } returns listOf(clientNode)
        every {
            nodeRepository.findAllByApplicationIdAndKindIn(1L, match { NodeKind.INFRASTRUCTURE in it && NodeKind.CLIENT in it }, any())
        } returns listOf(clientNode, infraNode)
        every { nodeRepository.findAllByApplicationIdAndKindIn(1L, any(), any()) } returns listOf(clientNode, infraNode)
        every { edgeRepository.findAllBySrcIdIn(any<Set<Long>>()) } returns emptyList()
        every { edgeRepository.findAllByDstIdIn(any()) } returns emptyList()

        val context =
            makeContext("какие интеграции есть в проекте?")
                .setMetadata(QueryMetadataKeys.APPLICATION_ID, 1L)

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val archText = result.context.getMetadata<String>(QueryMetadataKeys.ARCHITECTURE_CONTEXT_TEXT)
        assertThat(archText).isNotNull
        assertThat(archText).contains("Интеграции")
    }

    @Test
    fun `no applicationId produces error message`() {
        val context = makeContext("архитектура системы")

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val archText = result.context.getMetadata<String>(QueryMetadataKeys.ARCHITECTURE_CONTEXT_TEXT)
        assertThat(archText).contains("Не указан applicationId")
    }

    @Test
    fun `transitions to VECTOR_SEARCH`() {
        val transitions = step.getTransitions()
        assertThat(transitions["SUCCESS"]).isEqualTo(ProcessingStepType.VECTOR_SEARCH)
    }

    private fun makeNode(
        id: Long,
        kind: NodeKind,
        name: String,
        meta: Map<String, Any> = emptyMap(),
    ): Node =
        Node(
            id = id,
            application = app,
            fqn = "com.example.$name",
            name = name,
            kind = kind,
            lang = Lang.kotlin,
            meta = meta,
        )

    private fun makeContext(query: String): QueryProcessingContext =
        QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = "test-session",
        )
}
