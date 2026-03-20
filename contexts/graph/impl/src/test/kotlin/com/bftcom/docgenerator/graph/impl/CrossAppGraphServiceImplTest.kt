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

    // =====================================================================
    //  Basic / empty cases
    // =====================================================================

    @Test
    fun `buildCrossAppGraph with empty applications returns empty graph`() {
        every { applicationRepo.findAll() } returns emptyList()

        val result = service.buildCrossAppGraph()

        assertThat(result.nodes).isEmpty()
        assertThat(result.edges).isEmpty()
        assertThat(result.statistics.applicationCount).isEqualTo(0)
    }

    // =====================================================================
    //  Strategy B: Synthetic Node Matching
    // =====================================================================

    @Test
    fun `buildCrossAppGraph with multiple apps sharing HTTP endpoint via synthetic nodes`() {
        val app1 = createApp(1L, "app1", "Application 1")
        val app2 = createApp(2L, "app2", "Application 2")

        val endpoint1 = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users", kind = NodeKind.ENDPOINT
        )
        val endpoint2 = createSyntheticNode(
            id = 20L, app = app2,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users", kind = NodeKind.ENDPOINT
        )

        val method1 = createNode(11L, app1, "com.example.UserService.getUsers", NodeKind.METHOD)
        val method2 = createNode(21L, app2, "com.example.UserController.fetchUsers", NodeKind.METHOD)

        val edge1 = createEdge(method1, endpoint1, EdgeKind.CALLS_HTTP)
        val edge2 = createEdge(method2, endpoint2, EdgeKind.CALLS_HTTP)

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        // Strategy A: no interfaces
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        // Strategy B: synthetic endpoint nodes
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(endpoint1, endpoint2)
        every { edgeRepo.findAllByDstId(10L) } returns listOf(edge1)
        every { edgeRepo.findAllByDstId(20L) } returns listOf(edge2)
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(20L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.HTTP),
            limit = 1000
        )

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
        val app1 = createApp(1L, "app1", "Application 1")

        val httpEndpoint = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:http:POST:https://api.example.com/orders",
            name = "POST /orders", kind = NodeKind.ENDPOINT
        )
        val kafkaTopic = createSyntheticNode(
            id = 20L, app = app1,
            fqn = "infra:kafka:topic:order-events",
            name = "order-events", kind = NodeKind.TOPIC
        )

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(httpEndpoint, kafkaTopic)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        // Filter only HTTP
        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            integrationTypes = setOf(IntegrationType.HTTP),
            limit = 1000
        )

        assertThat(result.nodes).hasSize(2) // app1, httpEndpoint
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)
        assertThat(result.nodes.filter { it.kind == "TOPIC" }).isEmpty()
        assertThat(result.statistics.httpEndpoints).isEqualTo(1)
        assertThat(result.statistics.kafkaTopics).isEqualTo(0)
    }

    @Test
    fun `buildCrossAppGraph with Kafka topics and producers consumers`() {
        val app1 = createApp(1L, "producer-app", "Producer Application")
        val app2 = createApp(2L, "consumer-app", "Consumer Application")

        val topic1 = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:kafka:topic:user-events",
            name = "user-events", kind = NodeKind.TOPIC
        )
        val topic2 = createSyntheticNode(
            id = 20L, app = app2,
            fqn = "infra:kafka:topic:user-events",
            name = "user-events", kind = NodeKind.TOPIC
        )

        val producerMethod = createNode(11L, app1, "com.example.UserService.publishEvent", NodeKind.METHOD)
        val consumerMethod = createNode(21L, app2, "com.example.EventListener.onUserEvent", NodeKind.METHOD)

        val produceEdge = createEdge(producerMethod, topic1, EdgeKind.PRODUCES)
        val consumeEdge = createEdge(consumerMethod, topic2, EdgeKind.CONSUMES)

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(topic1, topic2)
        every { edgeRepo.findAllByDstId(10L) } returns listOf(produceEdge)
        every { edgeRepo.findAllByDstId(20L) } returns listOf(consumeEdge)
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(20L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.KAFKA),
            limit = 1000
        )

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
        val app1 = createApp(1L, "app1", "Application 1")

        val syntheticEndpoint = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:http:GET:https://api.example.com/users",
            name = "GET /users", kind = NodeKind.ENDPOINT
        )
        val nonSyntheticEndpoint = createNode(
            id = 20L, app = app1,
            fqn = "com.example.UserEndpoint", kind = NodeKind.ENDPOINT,
            meta = mapOf("synthetic" to false)
        )

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(syntheticEndpoint, nonSyntheticEndpoint)
        every { edgeRepo.findAllByDstId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 1000
        )

        // Only synthetic node should be included as integration point
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)
        assertThat(result.nodes.first { it.kind == "ENDPOINT" }.label).isEqualTo("GET /users")
    }

    @Test
    fun `buildCrossAppGraph respects limit parameter`() {
        val app1 = createApp(1L, "app1", "Application 1")

        val endpoints = (1..10).map { i ->
            createSyntheticNode(
                id = i.toLong() * 10, app = app1,
                fqn = "infra:http:GET:https://api.example.com/endpoint$i",
                name = "GET /endpoint$i", kind = NodeKind.ENDPOINT
            )
        }

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns endpoints
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 5
        )

        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSizeLessThanOrEqualTo(5)
    }

    // =====================================================================
    //  Strategy B: Legacy FQN format (VirtualNodeFactory endpoint://)
    // =====================================================================

    @Test
    fun `buildCrossAppGraph normalizes legacy endpoint FQN format`() {
        val app1 = createApp(1L, "app1", "Application 1")

        val legacyEndpoint = createNode(
            id = 10L, app = app1,
            fqn = "endpoint://GET https://api.example.com/users",
            kind = NodeKind.ENDPOINT,
            name = "GET /users",
            meta = mapOf("source" to "library_analysis", "httpMethod" to "GET")
        )

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(legacyEndpoint)
        every { edgeRepo.findAllByDstId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 1000
        )

        // Legacy node should be included (source=library_analysis treated as virtual)
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)
        // FQN should be normalized in the integration ID
        val endpointNode = result.nodes.first { it.kind == "ENDPOINT" }
        val fqn = endpointNode.metadata["fqn"] as String
        assertThat(fqn).startsWith("infra:http:")
    }

    // =====================================================================
    //  Strategy A: API Contract Matching
    // =====================================================================

    @Test
    fun `API contract matching detects shared interface across two apps`() {
        val app1 = createApp(1L, "ups", "UPS Service")
        val app2 = createApp(2L, "uds", "UDS Service")

        val ifaceFqn = "com.bftcom.ups.api.UpsApiContract"

        // Interface nodes in both apps
        val iface1 = createNode(
            id = 100L, app = app1, fqn = ifaceFqn,
            kind = NodeKind.INTERFACE, name = "UpsApiContract"
        )
        val iface2 = createNode(
            id = 200L, app = app2, fqn = ifaceFqn,
            kind = NodeKind.INTERFACE, name = "UpsApiContract"
        )

        // Provider: app1 has a class implementing the interface
        val controllerNode = createNode(
            id = 101L, app = app1, fqn = "com.bftcom.ups.controller.UpsController",
            kind = NodeKind.CLASS, name = "UpsController",
            meta = mapOf("supertypesSimple" to listOf("UpsApiContract"))
        )

        // Method children of interface (for methodCount)
        val ifaceMethod = createNode(
            id = 110L, app = app1, fqn = "$ifaceFqn.getUser",
            kind = NodeKind.METHOD, name = "getUser"
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.CLASS, NodeKind.ENDPOINT, NodeKind.SERVICE)) } returns
            listOf(controllerNode)
        every { nodeRepo.findAllByParentId(100L) } returns listOf(ifaceMethod)
        every { nodeRepo.findAllByParentId(200L) } returns emptyList()
        // Strategy B: no synthetic nodes
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        // Should find API_CONTRACT node
        val contractNodes = result.nodes.filter { it.kind == "API_CONTRACT" }
        assertThat(contractNodes).hasSize(1)
        assertThat(contractNodes[0].label).isEqualTo("UpsApiContract")
        assertThat(contractNodes[0].metadata["interfaceFqn"]).isEqualTo(ifaceFqn)

        // Should have PROVIDES edge from app1 and CONSUMES edge from app2
        val providesEdges = result.edges.filter { it.kind == "PROVIDES" }
        val consumesEdges = result.edges.filter { it.kind == "CONSUMES" }
        assertThat(providesEdges).hasSize(1)
        assertThat(providesEdges[0].source).isEqualTo("app:1")
        assertThat(consumesEdges).hasSize(1)
        assertThat(consumesEdges[0].source).isEqualTo("app:2")

        assertThat(result.statistics.apiContracts).isEqualTo(1)
    }

    @Test
    fun `API contract matching with provider detected via supertypesResolved`() {
        val app1 = createApp(1L, "ups", "UPS Service")
        val app2 = createApp(2L, "uds", "UDS Service")

        val ifaceFqn = "com.bftcom.ups.api.UpsApiContract"

        val iface1 = createNode(100L, app1, ifaceFqn, NodeKind.INTERFACE, "UpsApiContract")
        val iface2 = createNode(200L, app2, ifaceFqn, NodeKind.INTERFACE, "UpsApiContract")

        // Provider detected via supertypesResolved (full FQN match)
        val controllerNode = createNode(
            id = 101L, app = app1, fqn = "com.bftcom.ups.controller.UpsController",
            kind = NodeKind.CLASS, name = "UpsController",
            meta = mapOf("supertypesResolved" to listOf(ifaceFqn))
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.CLASS, NodeKind.ENDPOINT, NodeKind.SERVICE)) } returns
            listOf(controllerNode)
        every { nodeRepo.findAllByParentId(100L) } returns emptyList()
        every { nodeRepo.findAllByParentId(200L) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        val providesEdges = result.edges.filter { it.kind == "PROVIDES" }
        assertThat(providesEdges).hasSize(1)
        assertThat(providesEdges[0].source).isEqualTo("app:1")

        val consumesEdges = result.edges.filter { it.kind == "CONSUMES" }
        assertThat(consumesEdges).hasSize(1)
        assertThat(consumesEdges[0].source).isEqualTo("app:2")
    }

    @Test
    fun `no shared interface yields no API contract results`() {
        val app1 = createApp(1L, "app1", "Application 1")
        val app2 = createApp(2L, "app2", "Application 2")

        // Each app has its own unique interface
        val iface1 = createNode(100L, app1, "com.app1.Api1", NodeKind.INTERFACE, "Api1")
        val iface2 = createNode(200L, app2, "com.app2.Api2", NodeKind.INTERFACE, "Api2")

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        assertThat(result.nodes.filter { it.kind == "API_CONTRACT" }).isEmpty()
        assertThat(result.statistics.apiContracts).isEqualTo(0)
    }

    // =====================================================================
    //  Edge key parsing: FQN with colons doesn't break
    // =====================================================================

    @Test
    fun `edge key parsing with FQN containing colons`() {
        val app1 = createApp(1L, "app1", "Application 1")

        // FQN with many colons: infra:http:GET:https://api:8080/users
        val endpoint = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:http:GET:https://api:8080/users",
            name = "GET /users", kind = NodeKind.ENDPOINT
        )

        val method = createNode(11L, app1, "com.example.UserService.getUsers", NodeKind.METHOD)
        val edge = createEdge(method, endpoint, EdgeKind.CALLS_HTTP)

        every { applicationRepo.findAllById(listOf(1L)) } returns listOf(app1)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(endpoint)
        every { edgeRepo.findAllByDstId(10L) } returns listOf(edge)
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L),
            limit = 1000
        )

        // Should not crash and should produce valid edge
        assertThat(result.edges).hasSize(1)
        assertThat(result.edges[0].source).isEqualTo("app:1")
        assertThat(result.edges[0].kind).isEqualTo("CALLS_HTTP")
    }

    // =====================================================================
    //  Deduplication: both strategies find same point
    // =====================================================================

    @Test
    fun `deduplication when both strategies find same integration point`() {
        val app1 = createApp(1L, "ups", "UPS")
        val app2 = createApp(2L, "uds", "UDS")

        val ifaceFqn = "com.bftcom.ups.api.UpsApi"

        // Strategy A: shared interface
        val iface1 = createNode(100L, app1, ifaceFqn, NodeKind.INTERFACE, "UpsApi")
        val iface2 = createNode(200L, app2, ifaceFqn, NodeKind.INTERFACE, "UpsApi")

        val controller = createNode(
            101L, app1, "com.bftcom.ups.UpsController",
            NodeKind.CLASS, "UpsController",
            meta = mapOf("supertypesSimple" to listOf("UpsApi"))
        )

        // Strategy B: synthetic node for same endpoint
        val syntheticEndpoint = createSyntheticNode(
            id = 10L, app = app1,
            fqn = "infra:http:GET:https://ups.example.com/api",
            name = "GET /api", kind = NodeKind.ENDPOINT
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.CLASS, NodeKind.ENDPOINT, NodeKind.SERVICE)) } returns
            listOf(controller)
        every { nodeRepo.findAllByParentId(100L) } returns emptyList()
        every { nodeRepo.findAllByParentId(200L) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(syntheticEndpoint)
        every { edgeRepo.findAllByDstId(10L) } returns emptyList()
        every { edgeRepo.findAllBySrcId(10L) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        // Should have both: API_CONTRACT from strategy A and ENDPOINT from strategy B
        // They are different integration points (different IDs), both should be present
        assertThat(result.nodes.filter { it.kind == "API_CONTRACT" }).hasSize(1)
        assertThat(result.nodes.filter { it.kind == "ENDPOINT" }).hasSize(1)

        // Total = 2 apps + 1 contract + 1 endpoint = 4
        assertThat(result.nodes).hasSize(4)

        assertThat(result.statistics.apiContracts).isEqualTo(1)
        assertThat(result.statistics.httpEndpoints).isEqualTo(1)
    }

    // =====================================================================
    //  API Contract skipped when only KAFKA filter is active
    // =====================================================================

    @Test
    fun `API contracts skipped when only KAFKA integration type requested`() {
        val app1 = createApp(1L, "app1", "App 1")
        val app2 = createApp(2L, "app2", "App 2")

        val ifaceFqn = "com.shared.SharedApi"
        val iface1 = createNode(100L, app1, ifaceFqn, NodeKind.INTERFACE, "SharedApi")
        val iface2 = createNode(200L, app2, ifaceFqn, NodeKind.INTERFACE, "SharedApi")

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(app1, app2)
        // Strategy A queries should not run (KAFKA only), but we mock INTERFACE just in case
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.KAFKA),
            limit = 1000
        )

        // API contracts are HTTP-based, should be skipped
        assertThat(result.nodes.filter { it.kind == "API_CONTRACT" }).isEmpty()
        assertThat(result.statistics.apiContracts).isEqualTo(0)
    }

    // =====================================================================
    //  Strategy C: Endpoint Path Matching
    // =====================================================================

    @Test
    fun `Strategy C matches server endpoint with client synthetic node by path`() {
        val serverApp = createApp(1L, "ups-server", "UPS Server")
        val clientApp = createApp(2L, "uds-client", "UDS Client")

        // Server-side: non-synthetic ENDPOINT with apiMetadata
        val serverParent = createNode(
            id = 100L, app = serverApp,
            fqn = "com.bftcom.ups.controller.UpsController",
            kind = NodeKind.ENDPOINT, name = "UpsController",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "basePath" to "/ups/v1"))
        )
        val serverEndpoint = createNodeWithParent(
            id = 101L, app = serverApp,
            fqn = "com.bftcom.ups.controller.UpsController.findEstoDto",
            kind = NodeKind.ENDPOINT, name = "findEstoDto",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "method" to "GET", "path" to "/findEstoDto")),
            parent = serverParent
        )

        // Client-side: synthetic ENDPOINT from FQN
        val clientEndpoint = createSyntheticNode(
            id = 201L, app = clientApp,
            fqn = "infra:http:GET:https://ups-service:8080/ups/v1/findEstoDto",
            name = "GET /ups/v1/findEstoDto", kind = NodeKind.ENDPOINT
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(serverApp, clientApp)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(serverEndpoint, clientEndpoint)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            integrationTypes = setOf(IntegrationType.HTTP),
            limit = 1000
        )

        // Should find endpoint-match node
        val matchNodes = result.nodes.filter { it.metadata["matchStrategy"] == "endpoint-path" }
        assertThat(matchNodes).hasSize(1)
        assertThat(matchNodes[0].kind).isEqualTo("ENDPOINT")
        assertThat(matchNodes[0].label).isEqualTo("GET /ups/v1/findestodto")

        // Should have PROVIDES from server and CALLS_HTTP from client
        val providesEdges = result.edges.filter { it.kind == "PROVIDES" && it.target == matchNodes[0].id }
        val callsEdges = result.edges.filter { it.kind == "CALLS_HTTP" && it.target == matchNodes[0].id }
        assertThat(providesEdges).hasSize(1)
        assertThat(providesEdges[0].source).isEqualTo("app:1")
        assertThat(callsEdges).hasSize(1)
        assertThat(callsEdges[0].source).isEqualTo("app:2")

        assertThat(result.statistics.endpointMatches).isEqualTo(1)
    }

    @Test
    fun `Strategy C with basePath from parent node`() {
        val serverApp = createApp(1L, "server", "Server")
        val clientApp = createApp(2L, "client", "Client")

        // Server: controller with basePath, method endpoint without basePath in its own apiMetadata
        val controller = createNode(
            id = 100L, app = serverApp,
            fqn = "com.example.Controller",
            kind = NodeKind.ENDPOINT, name = "Controller",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "basePath" to "/api/v2"))
        )
        val serverMethod = createNodeWithParent(
            id = 101L, app = serverApp,
            fqn = "com.example.Controller.getItems",
            kind = NodeKind.ENDPOINT, name = "getItems",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "method" to "GET", "path" to "/items")),
            parent = controller
        )

        // Client: synthetic node calling /api/v2/items
        val clientNode = createSyntheticNode(
            id = 201L, app = clientApp,
            fqn = "infra:http:GET:https://server:8080/api/v2/items",
            name = "GET /api/v2/items", kind = NodeKind.ENDPOINT
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(serverApp, clientApp)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(serverMethod, clientNode)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        val matchNodes = result.nodes.filter { it.metadata["matchStrategy"] == "endpoint-path" }
        assertThat(matchNodes).hasSize(1)
        assertThat(result.statistics.endpointMatches).isEqualTo(1)
    }

    @Test
    fun `Strategy C no match when paths differ`() {
        val serverApp = createApp(1L, "server", "Server")
        val clientApp = createApp(2L, "client", "Client")

        val serverEndpoint = createNodeWithParent(
            id = 101L, app = serverApp,
            fqn = "com.example.Controller.getUsers",
            kind = NodeKind.ENDPOINT, name = "getUsers",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "method" to "GET", "path" to "/users")),
            parent = null
        )

        val clientNode = createSyntheticNode(
            id = 201L, app = clientApp,
            fqn = "infra:http:GET:https://server:8080/api/orders",
            name = "GET /api/orders", kind = NodeKind.ENDPOINT
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(serverApp, clientApp)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(serverEndpoint, clientNode)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        val matchNodes = result.nodes.filter { it.metadata["matchStrategy"] == "endpoint-path" }
        assertThat(matchNodes).isEmpty()
        assertThat(result.statistics.endpointMatches).isEqualTo(0)
    }

    @Test
    fun `Strategy C deduplicates with Strategy A results`() {
        val serverApp = createApp(1L, "ups", "UPS")
        val clientApp = createApp(2L, "uds", "UDS")

        val ifaceFqn = "com.bftcom.ups.api.UpsApi"

        // Strategy A: shared interface
        val iface1 = createNode(100L, serverApp, ifaceFqn, NodeKind.INTERFACE, "UpsApi")
        val iface2 = createNode(200L, clientApp, ifaceFqn, NodeKind.INTERFACE, "UpsApi")

        val controller = createNode(
            101L, serverApp, "com.bftcom.ups.UpsController",
            NodeKind.CLASS, "UpsController",
            meta = mapOf("supertypesSimple" to listOf("UpsApi"))
        )

        // Strategy C: server endpoint with apiMetadata
        val controllerEndpoint = createNode(
            id = 102L, app = serverApp,
            fqn = "com.bftcom.ups.UpsController",
            kind = NodeKind.ENDPOINT, name = "UpsController",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "basePath" to "/ups/v1"))
        )
        val serverEndpoint = createNodeWithParent(
            id = 103L, app = serverApp,
            fqn = "com.bftcom.ups.UpsController.getData",
            kind = NodeKind.ENDPOINT, name = "getData",
            meta = mapOf("apiMetadata" to mapOf("@type" to "HttpEndpoint", "method" to "GET", "path" to "/data")),
            parent = controllerEndpoint
        )
        val clientSynthetic = createSyntheticNode(
            id = 301L, app = clientApp,
            fqn = "infra:http:GET:https://ups:8080/ups/v1/data",
            name = "GET /ups/v1/data", kind = NodeKind.ENDPOINT
        )

        every { applicationRepo.findAllById(listOf(1L, 2L)) } returns listOf(serverApp, clientApp)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.INTERFACE)) } returns
            listOf(iface1, iface2)
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.CLASS, NodeKind.ENDPOINT, NodeKind.SERVICE)) } returns
            listOf(controller)
        every { nodeRepo.findAllByParentId(100L) } returns emptyList()
        every { nodeRepo.findAllByParentId(200L) } returns emptyList()
        every { nodeRepo.findAllByApplicationIdInAndKindIn(listOf(1L, 2L), setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)) } returns
            listOf(serverEndpoint, clientSynthetic)
        every { edgeRepo.findAllByDstId(any()) } returns emptyList()
        every { edgeRepo.findAllBySrcId(any()) } returns emptyList()

        val result = service.buildCrossAppGraph(
            applicationIds = listOf(1L, 2L),
            limit = 1000
        )

        // Strategy A produces API_CONTRACT, Strategy C produces ENDPOINT match
        // Both should be present (different integration IDs, no collision)
        val contractNodes = result.nodes.filter { it.kind == "API_CONTRACT" }
        val matchNodes = result.nodes.filter { it.metadata["matchStrategy"] == "endpoint-path" }
        assertThat(contractNodes).hasSize(1)
        assertThat(matchNodes).hasSize(1)

        assertThat(result.statistics.apiContracts).isEqualTo(1)
        assertThat(result.statistics.endpointMatches).isEqualTo(1)
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private fun createApp(id: Long, key: String, name: String): Application {
        val app = mockk<Application>(relaxed = true)
        every { app.id } returns id
        every { app.key } returns key
        every { app.name } returns name
        every { app.description } returns "Description for $name"
        return app
    }

    private fun createSyntheticNode(
        id: Long,
        app: Application,
        fqn: String,
        name: String,
        kind: NodeKind
    ): Node {
        return createNode(
            id = id, app = app, fqn = fqn, kind = kind,
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

    private fun createNodeWithParent(
        id: Long,
        app: Application,
        fqn: String,
        kind: NodeKind,
        name: String? = null,
        meta: Map<String, Any> = emptyMap(),
        parent: Node?
    ): Node {
        val node = createNode(id, app, fqn, kind, name, meta)
        every { node.parent } returns parent
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
