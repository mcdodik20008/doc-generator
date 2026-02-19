package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.dto.IntegrationType
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrossAppGraphServiceImplTest {
    private lateinit var applicationRepo: ApplicationRepository
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var service: CrossAppGraphServiceImpl

    @BeforeEach
    fun setUp() {
        applicationRepo = mockk()
        nodeRepo = mockk()
        edgeRepo = mockk()
        service = CrossAppGraphServiceImpl(applicationRepo, nodeRepo, edgeRepo)
    }

    @Test
    fun `buildCrossAppGraph with empty applications returns empty graph`() {
        // Given
        every { applicationRepo.findAll() } returns emptyList()

        // When
        val result = service.buildCrossAppGraph()

        // Then
        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        assertThat(result.statistics.applicationCount).isEqualTo(0)
    }

    @Test
    fun `buildCrossAppGraph with multiple apps sharing HTTP endpoint`() {
        // Given
        val app1 = createApp(1L, "app1", "Application 1")
        val app2 = createApp(2L, "app2", "Application 2")

        val endpoint1 = createIntegrationNode(
            id = 10L,
            app = app1,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users",
            kind = NodeKind.ENDPOINT
        )
        val endpoint2 = createIntegrationNode(
            id = 20L,
            app = app2,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users",
            kind = NodeKind.ENDPOINT
        )

        val method1 = createNode(11L, app1, "com.example.UserService.getUsers", NodeKind.METHOD)
        val method2 = createNode(21L, app2, "com.example.UserController.fetchUsers", NodeKind.METHOD)

        val edge1 = createEdge(method1, endpoint1, EdgeKind.CALLS_HTTP)
        val edge2 = createEdge(method2, endpoint2, EdgeKind.CALLS_HTTP)

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(endpoint1, endpoint2)
        every { edgeRepo.findAllByDstId(10L) } returns listOf(edge1)
        every { edgeRepo.findAllByDstId(20L) } returns listOf(edge2)
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(20L) } returns emptyList()

        // When
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.HTTP),
            limit = 1000
        )

        // Then
        assertThat(result.nodes).hasSize(3) // app1, app2, shared endpoint
        assertThat(result.nodes.filter { it.kind == "APPLICATION" }).hasSize(2)
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)

        assertThat(result.edges).hasSize(2) // app1→endpoint, app2→endpoint
        assertThat(result.edges.all { it.kind == "CALLS_HTTP" }).isTrue()

        assertThat(result.statistics.applicationCount).isEqualTo(2)
        assertThat(result.statistics.httpEndpoints).isEqualTo(1)
        assertThat(result.statistics.kafkaTopics).isEqualTo(0)
        assertThat(result.statistics.totalEdges).isEqualTo(2)
    }

    @Test
    fun `buildCrossAppGraph filters by integration type`() {
        // Given
        val app1 = createApp(1L, "app1", "Application 1")

        val httpEndpoint = createIntegrationNode(
            id = 10L,
            app = app1,
            fqn = "infra:http:POST:https://api.example.com/orders",
            name = "POST /orders",
            kind = NodeKind.ENDPOINT
        )
        val kafkaTopic = createIntegrationNode(
            id = 20L,
            app = app1,
            fqn = "infra:kafka:topic:order-events",
            name = "order-events",
            kind = NodeKind.TOPIC
        )

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(httpEndpoint, kafkaTopic)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        // When - filter only HTTP
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            integrationTypes = setOf(IntegrationType.HTTP),
            limit = 1000
        )

        // Then
        assertThat(result.nodes).hasSize(2) // app1, httpEndpoint (kafkaTopic excluded)
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)
        assertThat(result.nodes.filter { it.kind == "TOPIC" }).isEmpty()
        assertThat(result.statistics.httpEndpoints).isEqualTo(1)
        assertThat(result.statistics.kafkaTopics).isEqualTo(0)
    }

    @Test
    fun `buildCrossAppGraph with Kafka topics and producers consumers`() {
        // Given
        val app1 = createApp(1L, "producer-app", "Producer Application")
        val app2 = createApp(2L, "consumer-app", "Consumer Application")

        val topic1 = createIntegrationNode(
            id = 10L,
            app = app1,
            fqn = "infra:kafka:topic:user-events",
            name = "user-events",
            kind = NodeKind.TOPIC
        )
        val topic2 = createIntegrationNode(
            id = 20L,
            app = app2,
            fqn = "infra:kafka:topic:user-events",
            name = "user-events",
            kind = NodeKind.TOPIC
        )

        val producerMethod = createNode(11L, app1, "com.example.UserService.publishEvent", NodeKind.METHOD)
        val consumerMethod = createNode(21L, app2, "com.example.EventListener.onUserEvent", NodeKind.METHOD)

        val produceEdge = createEdge(producerMethod, topic1, EdgeKind.PRODUCES)
        val consumeEdge = createEdge(consumerMethod, topic2, EdgeKind.CONSUMES)

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(topic1, topic2)
        every { edgeRepo.findAllByDstId(10L) } returns listOf(produceEdge)
        every { edgeRepo.findAllByDstId(20L) } returns listOf(consumeEdge)
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(20L) } returns emptyList()

        // When
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.KAFKA),
            limit = 1000
        )

        // Then
        assertThat(result.nodes).hasSize(3) // app1, app2, topic
        assertThat(result.nodes.filter { it.kind == "TOPIC" }).hasSize(1)

        assertThat(result.edges).hasSize(2)
        val edgeKinds = result.edges.map { it.kind }.toSet()
        assertThat(edgeKinds).contains("PRODUCES", "CONSUMES")

        assertThat(result.statistics.kafkaTopics).isEqualTo(1)
        assertThat(result.statistics.httpEndpoints).isEqualTo(0)
    }

    @Test
    fun `buildCrossAppGraph excludes non-synthetic nodes`() {
        // Given
        val app1 = createApp(1L, "app1", "Application 1")

        val syntheticEndpoint = createIntegrationNode(
            id = 10L,
            app = app1,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users",
            kind = NodeKind.ENDPOINT
        )
        val nonSyntheticEndpoint = createNode(
            id = 20L,
            app = app1,
            fqn = "com.example.UserEndpoint",
            kind = NodeKind.ENDPOINT,
            meta = mapOf("synthetic" to false) // Not synthetic
        )

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(syntheticEndpoint, nonSyntheticEndpoint)
        every { edgeRepo.findAllByDstId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()

        // When
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 1000
        )

        // Then - only synthetic node should be included
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)
        assertThat(result.nodes.first { it.kind == "ENDPOINT" }.label).isEqualTo("GET /users")
    }

    @Test
    fun `buildCrossAppGraph respects limit parameter`() {
        // Given
        val app1 = createApp(1L, "app1", "Application 1")

        val endpoints = (1..10).map { i ->
            createIntegrationNode(
                id = i.toLong() * 10,
                app = app1,
                fqn = "infra:http:GET:https://api.example.com/endpoint$i",
                name = "GET /endpoint$i",
                kind = NodeKind.ENDPOINT
            )
        }

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns endpoints
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        // When
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 5
        )

        // Then
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSizeLessThanOrEqualTo(5)
    }

    // Helper methods

    private fun createApp(id: Long, key: String, name: String): Application {
        val app = mockk<Application>(relaxed = true)
        every { app.id } returns id
        every { app.key } returns key
        every { app.name } returns name
        every { app.description } returns "Description for $name"
        return app
    }

    private fun createIntegrationNode(
        id: Long,
        app: Application,
        fqn: String,
        name: String,
        kind: NodeKind
    ): Node {
        return createNode(
            id = id,
            app = app,
            fqn = fqn,
            kind = kind,
            name = name,
            meta = mapOf("synthetic" to true, "origin" to "linker")
        )
    }

    private fun createNode(
        id: Long,
        app: Application,
        fqn: String,
        kind: NodeKind,
        name: String? = null,
        meta: Map<String, Any> = emptyMap()
    ): Node {
        val node = mockk<Node>(relaxed = true)
        every { node.id } returns id
        every { node.application } returns app
        every { node.fqn } returns fqn
        every { node.kind } returns kind
        every { node.name } returns name
        every { node.meta } returns meta
        every { node.lang } returns Lang.other
        return node
    }

    private fun createEdge(src: Node, dst: Node, kind: EdgeKind): Edge {
        val edge = mockk<Edge>(relaxed = true)
        every { edge.src } returns src
        every { edge.dst } returns dst
        every { edge.kind } returns kind
        return edge
    }
}
