package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.chunking.model.chunk.ChunkDetailsResponse
import com.bftcom.docgenerator.chunking.model.chunk.ChunkRelations
import com.bftcom.docgenerator.chunking.model.chunk.NodeBrief
import com.bftcom.docgenerator.chunking.model.chunk.RelationBrief
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
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
import org.junit.jupiter.api.assertThrows
import org.springframework.data.repository.findByIdOrNull

class ChunkDetailsServiceTest {
    private lateinit var chunkRepo: ChunkRepository
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var service: ChunkDetailsService

    @BeforeEach
    fun setUp() {
        chunkRepo = mockk(relaxed = true)
        nodeRepo = mockk(relaxed = true)
        edgeRepo = mockk(relaxed = true)
        service = ChunkDetailsService(chunkRepo, nodeRepo, edgeRepo)
    }

    @Test
    fun `getDetails - возвращает детали чанка с корректным nodeId`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L
        node.name = "Test"
        node.packageName = "com.example"

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
            metadata = mapOf("key" to "value"),
        )
        chunk.emb = floatArrayOf(1.0f, 2.0f, 3.0f)

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.id).isEqualTo("1")
        assertThat(result.title).isEqualTo("com.example.Test")
        assertThat(result.content).isEqualTo("Test content")
        assertThat(result.metadata).isEqualTo(mapOf("key" to "value"))
        assertThat(result.embeddingSize).isEqualTo(3)
        assertThat(result.node).isNotNull
        assertThat(result.node?.id).isEqualTo("100")
        assertThat(result.node?.kind).isEqualTo("CLASS")
        assertThat(result.node?.name).isEqualTo("Test")
        assertThat(result.node?.packageName).isEqualTo("com.example")
    }

    @Test
    fun `getDetails - возвращает детали чанка с incoming и outgoing relations`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L

        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val srcNode = Node(application = app, fqn = "com.example.Src", kind = NodeKind.CLASS, lang = Lang.kotlin)
        srcNode.id = 200L

        val dstNode = Node(application = app, fqn = "com.example.Dst", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = 300L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        val outgoingEdge = Edge(src = node, dst = dstNode, kind = EdgeKind.CALLS)
        outgoingEdge.src.id = 100L
        outgoingEdge.dst.id = 300L

        val incomingEdge = Edge(src = srcNode, dst = node, kind = EdgeKind.CALLS)
        incomingEdge.src.id = 200L
        incomingEdge.dst.id = 100L

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns listOf(outgoingEdge)
        every { edgeRepo.findAllByDstId(100L) } returns listOf(incomingEdge)

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.relations.outgoing).hasSize(1)
        assertThat(result.relations.outgoing[0].kind).isEqualTo("CALLS")
        assertThat(result.relations.outgoing[0].otherNodeId).isEqualTo("300")

        assertThat(result.relations.incoming).hasSize(1)
        assertThat(result.relations.incoming[0].kind).isEqualTo("CALLS")
        assertThat(result.relations.incoming[0].otherNodeId).isEqualTo("200")
    }

    @Test
    fun `getDetails - обрабатывает чанк с null node id возвращает fallback title`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = null // null id

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        every { chunkRepo.findByNodeId(any()) } returns mutableListOf(chunk)

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.id).isEqualTo("1")
        assertThat(result.title).isEqualTo("doc:explanation")
        assertThat(result.node).isNull()
        assertThat(result.relations).isEqualTo(ChunkRelations(emptyList(), emptyList()))
        assertThat(result.content).isEqualTo("Test content")
    }

    @Test
    fun `getDetails - выбрасывает исключение когда чанк не найден (empty list)`() {
        // given
        every { chunkRepo.findByNodeId(100L) } returns mutableListOf()

        // when & then
        assertThrows<NoSuchElementException> {
            service.getDetails("100")
        }
    }

    @Test
    fun `getDetails - обрабатывает чанк без relations (пустые списки incoming-outgoing)`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.relations.outgoing).isEmpty()
        assertThat(result.relations.incoming).isEmpty()
    }

    @Test
    fun `getDetails - обрабатывает чанк с null node (nodeRepo возвращает null)`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.empty() // node не найден
        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.title).isEqualTo("doc:explanation") // fallback title
        assertThat(result.node).isNull()
    }

    @Test
    fun `getDetails - корректно обрабатывает chunk kind null`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = null

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = null, // null kind
            content = "Test content",
        )

        every { chunkRepo.findByNodeId(any()) } returns mutableListOf(chunk)

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.title).isEqualTo("doc:unknown")
    }

    @Test
    fun `getDetails - корректно маппит embeddingSize из chunk emb size`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )
        chunk.emb = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f) // 5 элементов

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.embeddingSize).isEqualTo(5)
    }

    @Test
    fun `getDetails - корректно маппит EdgeKind name в RelationBrief kind`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L

        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val dstNode = Node(application = app, fqn = "com.example.Dst", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = 300L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        val edge = Edge(src = node, dst = dstNode, kind = EdgeKind.READS)
        edge.src.id = 100L
        edge.dst.id = 300L

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.relations.outgoing).hasSize(1)
        assertThat(result.relations.outgoing[0].kind).isEqualTo("READS")
    }

    @Test
    fun `getDetails - различает incoming и outgoing edges проверка src dst`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L

        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val srcNode = Node(application = app, fqn = "com.example.Src", kind = NodeKind.CLASS, lang = Lang.kotlin)
        srcNode.id = 200L

        val dstNode = Node(application = app, fqn = "com.example.Dst", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = 300L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )

        // outgoing: node -> dstNode
        val outgoingEdge = Edge(src = node, dst = dstNode, kind = EdgeKind.CALLS)
        outgoingEdge.src.id = 100L
        outgoingEdge.dst.id = 300L

        // incoming: srcNode -> node
        val incomingEdge = Edge(src = srcNode, dst = node, kind = EdgeKind.CALLS)
        incomingEdge.src.id = 200L
        incomingEdge.dst.id = 100L

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns listOf(outgoingEdge)
        every { edgeRepo.findAllByDstId(100L) } returns listOf(incomingEdge)

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.relations.outgoing).hasSize(1)
        assertThat(result.relations.outgoing[0].id).isEqualTo("100") // src id
        assertThat(result.relations.outgoing[0].otherNodeId).isEqualTo("300") // dst id

        assertThat(result.relations.incoming).hasSize(1)
        assertThat(result.relations.incoming[0].id).isEqualTo("100") // dst id в incoming
        assertThat(result.relations.incoming[0].otherNodeId).isEqualTo("200") // src id в incoming
    }

    @Test
    fun `getDetails - обрабатывает null embedding`() {
        // given
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Test", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val chunk = Chunk(
            id = 1L,
            application = app,
            node = node,
            source = "doc",
            kind = "explanation",
            content = "Test content",
        )
        chunk.emb = null // null embedding

        every { chunkRepo.findByNodeId(100L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(100L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()
        every { edgeRepo.findAllByDstId(100L) } returns emptyList()

        // when
        val result = service.getDetails("100")

        // then
        assertThat(result.embeddingSize).isNull()
    }
}
