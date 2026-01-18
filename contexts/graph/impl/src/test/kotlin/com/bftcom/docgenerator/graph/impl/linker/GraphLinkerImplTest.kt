package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeGraphSink
import com.bftcom.docgenerator.graph.impl.linker.edge.AnnotationEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.CallEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.InheritanceEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.IntegrationEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.SignatureDependencyLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.StructuralEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.ThrowEdgeLinker
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable

class GraphLinkerImplTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var sink: GraphSink
    private lateinit var libraryNodeSink: LibraryNodeGraphSink
    private lateinit var objectMapper: ObjectMapper

    private lateinit var structuralEdgeLinker: StructuralEdgeLinker
    private lateinit var inheritanceEdgeLinker: InheritanceEdgeLinker
    private lateinit var annotationEdgeLinker: AnnotationEdgeLinker
    private lateinit var signatureDependencyLinker: SignatureDependencyLinker
    private lateinit var callEdgeLinker: CallEdgeLinker
    private lateinit var throwEdgeLinker: ThrowEdgeLinker
    private lateinit var integrationEdgeLinker: IntegrationEdgeLinker

    private lateinit var graphLinker: GraphLinkerImpl

    private val app = Application(id = 1L, key = "app", name = "App")

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk()
        sink = mockk(relaxed = true)
        libraryNodeSink = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerKotlinModule()

        structuralEdgeLinker = mockk()
        inheritanceEdgeLinker = mockk()
        annotationEdgeLinker = mockk()
        signatureDependencyLinker = mockk()
        callEdgeLinker = mockk()
        throwEdgeLinker = mockk()
        integrationEdgeLinker = mockk()

        graphLinker =
            GraphLinkerImpl(
                nodeRepo = nodeRepo,
                nodeIndexFactory = NodeIndexFactory(),
                sink = sink,
                libraryNodeSink = libraryNodeSink,
                objectMapper = objectMapper,
                structuralEdgeLinker = structuralEdgeLinker,
                inheritanceEdgeLinker = inheritanceEdgeLinker,
                annotationEdgeLinker = annotationEdgeLinker,
                signatureDependencyLinker = signatureDependencyLinker,
                callEdgeLinker = callEdgeLinker,
                throwEdgeLinker = throwEdgeLinker,
                integrationEdgeLinker = integrationEdgeLinker,
            )
    }

    @Test
    fun `link - пустой список узлов не вызывает sink`() {
        every { nodeRepo.findAllByApplicationId(1L, any<Pageable>()) } returns emptyList()

        graphLinker.link(app)

        verify(exactly = 0) { sink.upsertEdges(any()) }
        verify(exactly = 0) { libraryNodeSink.upsertLibraryNodeEdges(any()) }
    }

    @Test
    fun `link - агрегирует рёбра и сохраняет в sink-и`() {
        val pkg = node(fqn = "com.example", name = "com.example", pkg = "com.example", kind = NodeKind.PACKAGE)
        val base = node(fqn = "com.example.Base", name = "Base", pkg = "com.example", kind = NodeKind.CLASS)
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                fqn = "com.example.Service.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                meta =
                    metaMap(
                        NodeMeta(
                            ownerFqn = "com.example.Service",
                            imports = listOf("com.example.Base"),
                        ),
                    ),
            )
        val all = listOf(pkg, base, type, fn)

        every { nodeRepo.findAllByApplicationId(1L, any<Pageable>()) } returns all

        val structuralEdges = listOf(Triple(pkg, type, EdgeKind.CONTAINS))
        every { structuralEdgeLinker.linkContains(all, any(), any()) } returns structuralEdges

        every { annotationEdgeLinker.link(any(), any(), any()) } returns emptyList()
        every { inheritanceEdgeLinker.link(type, any(), any()) } returns listOf(Triple(type, base, EdgeKind.INHERITS))
        every { signatureDependencyLinker.link(fn, any(), any()) } returns listOf(Triple(fn, base, EdgeKind.DEPENDS_ON))
        every { callEdgeLinker.link(fn, any(), any()) } returns listOf(Triple(fn, type, EdgeKind.CALLS_CODE))
        every { throwEdgeLinker.link(fn, any(), any()) } returns listOf(Triple(fn, base, EdgeKind.THROWS))

        val newEndpoint = node(fqn = "http://example", name = "GET /example", pkg = "virtual", kind = NodeKind.ENDPOINT)
        val lib = Library(id = 1L, coordinate = "g:a:1", groupId = "g", artifactId = "a", version = "1")
        val libNode = LibraryNode(id = 10L, library = lib, fqn = "com.lib.Fn", name = "Fn", kind = NodeKind.METHOD, lang = Lang.kotlin)
        val libEdges = listOf(LibraryNodeEdgeProposal(kind = EdgeKind.CALLS_CODE, node = fn, libraryNode = libNode))

        every { integrationEdgeLinker.linkIntegrationEdgesWithNodes(fn, any(), any(), app) } returns
            Triple(
                listOf(Triple(fn, newEndpoint, EdgeKind.CALLS_HTTP)),
                listOf(newEndpoint),
                libEdges,
            )

        val savedEdges = mutableListOf<EdgeProposal>()
        every { sink.upsertEdges(any()) } answers {
            savedEdges += firstArg<Sequence<EdgeProposal>>().toList()
        }
        val savedLibEdges = mutableListOf<LibraryNodeEdgeProposal>()
        every { libraryNodeSink.upsertLibraryNodeEdges(any()) } answers {
            savedLibEdges += firstArg<Sequence<LibraryNodeEdgeProposal>>().toList()
        }

        graphLinker.link(app)

        val triples = savedEdges.map { Triple(it.source, it.target, it.kind) }
        assertThat(triples).contains(
            Triple(pkg, type, EdgeKind.CONTAINS),
            Triple(type, base, EdgeKind.INHERITS),
            Triple(fn, base, EdgeKind.DEPENDS_ON),
            Triple(fn, type, EdgeKind.CALLS_CODE),
            Triple(fn, base, EdgeKind.THROWS),
            Triple(fn, newEndpoint, EdgeKind.CALLS_HTTP),
        )
        assertThat(savedLibEdges).containsExactlyElementsOf(libEdges)

        verify(exactly = 1) { sink.upsertEdges(any()) }
        verify(exactly = 1) { libraryNodeSink.upsertLibraryNodeEdges(any()) }
    }

    @Test
    fun `link - если CALLS линковка падает, integration не вызывается, но throws все равно линкится`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                fqn = "com.example.Service.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                meta = metaMap(NodeMeta(ownerFqn = "com.example.Service")),
            )
        val all = listOf(type, fn)

        every { nodeRepo.findAllByApplicationId(1L, any<Pageable>()) } returns all
        every { structuralEdgeLinker.linkContains(all, any(), any()) } returns emptyList()
        every { annotationEdgeLinker.link(any(), any(), any()) } returns emptyList()
        every { inheritanceEdgeLinker.link(any(), any(), any()) } returns emptyList()
        every { signatureDependencyLinker.link(any(), any(), any()) } returns emptyList()
        every { callEdgeLinker.link(fn, any(), any()) } throws RuntimeException("boom")
        every { throwEdgeLinker.link(fn, any(), any()) } returns listOf(Triple(fn, type, EdgeKind.THROWS))

        val savedEdges = mutableListOf<EdgeProposal>()
        every { sink.upsertEdges(any()) } answers {
            savedEdges += firstArg<Sequence<EdgeProposal>>().toList()
        }

        graphLinker.link(app)

        verify(exactly = 1) { throwEdgeLinker.link(fn, any(), any()) }
        verify(exactly = 0) { integrationEdgeLinker.linkIntegrationEdgesWithNodes(any(), any(), any(), any()) }
        verify(exactly = 0) { libraryNodeSink.upsertLibraryNodeEdges(any()) }

        val triples = savedEdges.map { Triple(it.source, it.target, it.kind) }
        assertThat(triples).contains(Triple(fn, type, EdgeKind.THROWS))
    }

    @Test
    fun `link - для type узла не вызывает function линкеры`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val all = listOf(type)

        every { nodeRepo.findAllByApplicationId(1L, any<Pageable>()) } returns all
        every { structuralEdgeLinker.linkContains(all, any(), any()) } returns emptyList()
        every { annotationEdgeLinker.link(any(), any(), any()) } returns emptyList()
        every { inheritanceEdgeLinker.link(type, any(), any()) } returns emptyList()

        graphLinker.link(app)

        verify(exactly = 1) { inheritanceEdgeLinker.link(type, any(), any()) }
        verify(exactly = 0) { signatureDependencyLinker.link(any(), any(), any()) }
        verify(exactly = 0) { callEdgeLinker.link(any(), any(), any()) }
        verify(exactly = 0) { integrationEdgeLinker.linkIntegrationEdgesWithNodes(any(), any(), any(), any()) }
        verify(exactly = 0) { throwEdgeLinker.link(any(), any(), any()) }
    }

    private fun node(
        fqn: String,
        name: String,
        pkg: String,
        kind: NodeKind,
        meta: Map<String, Any> = emptyMap(),
    ): Node =
        Node(
            id = null,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
            meta = meta,
        )

    private fun metaMap(meta: NodeMeta): Map<String, Any> =
        @Suppress("UNCHECKED_CAST")
        (objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>)
}

