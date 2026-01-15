package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkDetailsServiceTest {
    @Test
    fun `getDetails - возвращает node=null если nodeId отсутствует`() {
        val chunkRepo = mockk<ChunkRepository>()
        val nodeRepo = mockk<NodeRepository>(relaxed = true)
        val edgeRepo = mockk<EdgeRepository>(relaxed = true)

        val app = Application(key = "app", name = "App")
        val nodeWithoutId = Node(
            id = null,
            application = app,
            fqn = "com.example.Foo",
            name = "Foo",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val chunk = Chunk(
            id = 100L,
            application = app,
            node = nodeWithoutId,
            source = "code",
            content = "content",
            title = "Foo",
            pipeline = mapOf("p" to 1),
        )
        every { chunkRepo.findByNodeId(1L) } returns mutableListOf(chunk)

        val service = ChunkDetailsService(chunkRepo, nodeRepo, edgeRepo)
        val res = service.getDetails("1")

        assertNull(res.node)
        assertTrue(res.relations.incoming.isEmpty())
        assertTrue(res.relations.outgoing.isEmpty())
        assertEquals(mapOf("p" to 1), res.metadata)
    }

    @Test
    fun `getDetails - строит node и связи`() {
        val chunkRepo = mockk<ChunkRepository>()
        val nodeRepo = mockk<NodeRepository>()
        val edgeRepo = mockk<EdgeRepository>()

        val app = Application(id = 1L, key = "app", name = "App")
        val node = Node(
            id = 10L,
            application = app,
            fqn = "com.example.Foo.bar",
            name = "bar",
            packageName = "com.example",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        val dst = Node(
            id = 20L,
            application = app,
            fqn = "com.example.Baz.qux",
            name = "qux",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )

        val chunk = Chunk(
            id = 100L,
            application = app,
            node = node,
            source = "code",
            content = "content",
            title = "Foo.bar",
        )

        every { chunkRepo.findByNodeId(1L) } returns mutableListOf(chunk)
        every { nodeRepo.findById(10L) } returns java.util.Optional.of(node)
        every { edgeRepo.findAllBySrcId(10L) } returns listOf(Edge(src = node, dst = dst, kind = EdgeKind.CALLS_CODE))
        every { edgeRepo.findAllByDstId(10L) } returns listOf(Edge(src = dst, dst = node, kind = EdgeKind.CALLS))

        val service = ChunkDetailsService(chunkRepo, nodeRepo, edgeRepo)
        val res = service.getDetails("1")

        val brief = assertNotNull(res.node)
        assertEquals("10", brief.id)
        assertEquals("METHOD", brief.kind)
        assertEquals(1, res.relations.outgoing.size)
        assertEquals(1, res.relations.incoming.size)
    }
}

