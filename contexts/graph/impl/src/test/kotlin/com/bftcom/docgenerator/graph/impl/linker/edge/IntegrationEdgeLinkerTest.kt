package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.impl.linker.NodeIndexFactory
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import com.bftcom.docgenerator.graph.impl.linker.virtual.VirtualNodeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any

class IntegrationEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")
    private lateinit var libraryNodeIndex: LibraryNodeIndex
    private lateinit var integrationPointService: IntegrationPointService
    private lateinit var virtualNodeFactory: VirtualNodeFactory
    private lateinit var linker: IntegrationEdgeLinker

    @BeforeEach
    fun setUp() {
        libraryNodeIndex = mock(LibraryNodeIndex::class.java)
        integrationPointService = mock(IntegrationPointService::class.java)
        virtualNodeFactory = mock(VirtualNodeFactory::class.java)
        linker = IntegrationEdgeLinker(libraryNodeIndex, integrationPointService, virtualNodeFactory)
    }

    @Test
    fun `link - базовый метод возвращает пустой список`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))

        val edges = linker.link(fn, NodeMeta(), index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - null rawUsages возвращает пустой список`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(rawUsages = null)

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
        assertThat(result.third).isEmpty()
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - empty rawUsages возвращает пустой список`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(rawUsages = emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
        assertThat(result.third).isEmpty()
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Simple usage без owner не находит библиотечный метод`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            rawUsages = listOf(RawUsage.Simple("someMethod", isCall = true)),
            imports = emptyList(),
        )

        `when`(libraryNodeIndex.findByMethodFqn(any())).thenReturn(null)

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).isEmpty()
        assertThat(result.second).isEmpty()
        assertThat(result.third).isEmpty()
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Simple usage с owner находит метод через owner FQN`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val fn = node(fqn = "com.example.Owner.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(owner, fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("someMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.someMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.third).hasSize(1)
        assertThat(result.third.first().kind).isEqualTo(EdgeKind.CALLS_CODE)
        assertThat(result.third.first().node).isEqualTo(fn)
        assertThat(result.third.first().libraryNode).isEqualTo(libraryNode)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Simple usage через imports находит метод`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            rawUsages = listOf(RawUsage.Simple("someMethod", isCall = true)),
            imports = listOf("com.example.SomeClass"),
        )

        val libraryNode = mock(LibraryNode::class.java)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.SomeClass.someMethod.someMethod")).thenReturn(null)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.SomeClass.someMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.third).hasSize(1)
        assertThat(result.third.first().kind).isEqualTo(EdgeKind.CALLS_CODE)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Dot usage с uppercase receiver разрешает через type resolution`() {
        val receiverType = node(fqn = "com.example.Receiver", name = "Receiver", pkg = "com.example", kind = NodeKind.CLASS)
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(receiverType, fn))
        val meta = NodeMeta(
            rawUsages = listOf(RawUsage.Dot(receiver = "Receiver", member = "someMethod", isCall = true)),
            imports = listOf("com.example.Receiver"),
        )

        val libraryNode = mock(LibraryNode::class.java)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.Receiver.someMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.third).hasSize(1)
        assertThat(result.third.first().kind).isEqualTo(EdgeKind.CALLS_CODE)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Dot usage с lowercase receiver использует owner`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val fn = node(fqn = "com.example.Owner.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(owner, fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Dot(receiver = "owner", member = "someMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.someMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.third).hasSize(1)
        assertThat(result.third.first().kind).isEqualTo(EdgeKind.CALLS_CODE)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint создает CALLS_HTTP edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.second).contains(endpoint)
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_HTTP }
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_CODE }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint с hasRetry создает RETRIES_TO edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                    hasRetry = true,
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.RETRIES_TO))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.RETRIES_TO }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint с hasTimeout создает TIMEOUTS_TO edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                    hasTimeout = true,
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.TIMEOUTS_TO))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.TIMEOUTS_TO }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint с hasCircuitBreaker создает CIRCUIT_BREAKER_TO edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                    hasCircuitBreaker = true,
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CIRCUIT_BREAKER_TO))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CIRCUIT_BREAKER_TO }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint со всеми флагами создает все edges`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                    hasRetry = true,
                    hasTimeout = true,
                    hasCircuitBreaker = true,
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.RETRIES_TO))
        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.TIMEOUTS_TO))
        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CIRCUIT_BREAKER_TO))
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint с null url использует unknown`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET unknown", name = "unknown", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = null,
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("unknown", "GET", index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - HttpEndpoint с существующим endpoint не добавляет в newNodes`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)
        val index = NodeIndexFactory().create(listOf(fn, endpoint))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, false))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.second).isEmpty() // endpoint уже существует
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - KafkaTopic PRODUCE создает PRODUCES edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("kafkaMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val topic = node(fqn = "topic://my-topic", name = "my-topic", pkg = null, kind = NodeKind.TOPIC)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.kafkaMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.KafkaTopic(
                    methodId = "com.example.Owner.kafkaMethod",
                    topic = "my-topic",
                    operation = "PRODUCE",
                    clientType = "KafkaProducer",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateTopicNode("my-topic", index, app))
            .thenReturn(Pair(topic, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, topic, EdgeKind.PRODUCES))
        assertThat(result.second).contains(topic)
        assertThat(result.third).anyMatch { it.kind == EdgeKind.PRODUCES }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - KafkaTopic CONSUME создает CONSUMES edge`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("kafkaMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val topic = node(fqn = "topic://my-topic", name = "my-topic", pkg = null, kind = NodeKind.TOPIC)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.kafkaMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.KafkaTopic(
                    methodId = "com.example.Owner.kafkaMethod",
                    topic = "my-topic",
                    operation = "CONSUME",
                    clientType = "KafkaConsumer",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateTopicNode("my-topic", index, app))
            .thenReturn(Pair(topic, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, topic, EdgeKind.CONSUMES))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CONSUMES }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - KafkaTopic с null topic использует unknown`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("kafkaMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val topic = node(fqn = "topic://unknown", name = "unknown", pkg = null, kind = NodeKind.TOPIC)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.kafkaMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.KafkaTopic(
                    methodId = "com.example.Owner.kafkaMethod",
                    topic = null,
                    operation = "PRODUCE",
                    clientType = "KafkaProducer",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateTopicNode("unknown", index, app))
            .thenReturn(Pair(topic, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, topic, EdgeKind.PRODUCES))
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - CamelRoute с http endpointType создает CALLS_HTTP`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("camelMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://http://camel.example.com", name = "http://camel.example.com", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.camelMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.CamelRoute(
                    methodId = "com.example.Owner.camelMethod",
                    uri = "http://camel.example.com",
                    endpointType = "http",
                    direction = "OUT",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://camel.example.com", null, index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_HTTP }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - CamelRoute с uri начинающимся с http создает CALLS_HTTP`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("camelMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://http://camel.example.com", name = "http://camel.example.com", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.camelMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.CamelRoute(
                    methodId = "com.example.Owner.camelMethod",
                    uri = "http://camel.example.com",
                    endpointType = null,
                    direction = "OUT",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://camel.example.com", null, index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_HTTP }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - CamelRoute без http не создает CALLS_HTTP`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("camelMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://file:///tmp", name = "/tmp", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.camelMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.CamelRoute(
                    methodId = "com.example.Owner.camelMethod",
                    uri = "file:///tmp",
                    endpointType = "file",
                    direction = "OUT",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("file:///tmp", null, index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        // Для Camel без http не создается CALLS_HTTP в edges, но создается endpoint
        assertThat(result.first).isEmpty() // TODO: можно добавить другие типы Camel endpoints
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - CamelRoute с null uri использует unknown`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("camelMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://unknown", name = "unknown", pkg = null, kind = NodeKind.ENDPOINT)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.camelMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.CamelRoute(
                    methodId = "com.example.Owner.camelMethod",
                    uri = null,
                    endpointType = "http",
                    direction = "OUT",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("unknown", null, index, app))
            .thenReturn(Pair(endpoint, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - Simple usage с FQN методом находит библиотеку`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            rawUsages = listOf(RawUsage.Simple("com.example.SomeClass.method", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        `when`(libraryNodeIndex.findByMethodFqn("com.example.SomeClass.method")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(emptyList())

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.third).hasSize(1)
        assertThat(result.third.first().kind).isEqualTo(EdgeKind.CALLS_CODE)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - множественные интеграционные точки создают все edges`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("multiMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)
        val endpoint = node(fqn = "endpoint://GET http://example.com/api", name = "api", pkg = null, kind = NodeKind.ENDPOINT)
        val topic = node(fqn = "topic://my-topic", name = "my-topic", pkg = null, kind = NodeKind.TOPIC)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.multiMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.multiMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                ),
                IntegrationPoint.KafkaTopic(
                    methodId = "com.example.Owner.multiMethod",
                    topic = "my-topic",
                    operation = "PRODUCE",
                    clientType = "KafkaProducer",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(endpoint, true))
        `when`(virtualNodeFactory.getOrCreateTopicNode("my-topic", index, app))
            .thenReturn(Pair(topic, true))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        assertThat(result.first).contains(Triple(fn, endpoint, EdgeKind.CALLS_HTTP))
        assertThat(result.first).contains(Triple(fn, topic, EdgeKind.PRODUCES))
        assertThat(result.second).contains(endpoint)
        assertThat(result.second).contains(topic)
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - virtualNodeFactory возвращает null для endpoint`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("httpMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.httpMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.HttpEndpoint(
                    url = "http://example.com/api",
                    methodId = "com.example.Owner.httpMethod",
                    httpMethod = "GET",
                    clientType = "OkHttp",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateEndpointNode("http://example.com/api", "GET", index, app))
            .thenReturn(Pair(null, false))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        // Если endpoint не создан, edges для endpoint не создаются, но library edge создается
        assertThat(result.first).isEmpty()
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_CODE }
    }

    @Test
    fun `linkIntegrationEdgesWithNodes - virtualNodeFactory возвращает null для topic`() {
        val fn = node(fqn = "com.example.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("kafkaMethod", isCall = true)),
        )

        val libraryNode = mock(LibraryNode::class.java)

        `when`(libraryNodeIndex.findByMethodFqn("com.example.Owner.kafkaMethod")).thenReturn(libraryNode)
        `when`(integrationPointService.extractIntegrationPoints(libraryNode)).thenReturn(
            listOf(
                IntegrationPoint.KafkaTopic(
                    methodId = "com.example.Owner.kafkaMethod",
                    topic = "my-topic",
                    operation = "PRODUCE",
                    clientType = "KafkaProducer",
                ),
            ),
        )
        `when`(virtualNodeFactory.getOrCreateTopicNode("my-topic", index, app))
            .thenReturn(Pair(null, false))

        val result = linker.linkIntegrationEdgesWithNodes(fn, meta, index, app)

        // Если topic не создан, edges для topic не создаются, но library edge создается
        assertThat(result.first).isEmpty()
        assertThat(result.third).anyMatch { it.kind == EdgeKind.CALLS_CODE }
    }

    private fun node(
        fqn: String,
        name: String,
        pkg: String?,
        kind: NodeKind,
    ): Node =
        Node(
            id = null,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
        )
}
