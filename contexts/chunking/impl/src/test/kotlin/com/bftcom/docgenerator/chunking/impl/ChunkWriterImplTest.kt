package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.chunking.model.plan.PipelinePlan
import com.bftcom.docgenerator.chunking.model.plan.ServiceMeta
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChunkWriterImplTest {
    @Test
    fun `savePlan - сохраняет новые чанки и возвращает written`() {
        val repo = mockk<ChunkRepository>()
        every { repo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { repo.save(any()) } answers { firstArg() }

        val writer = ChunkWriterImpl(repo)
        val plan1 = plan(nodeId = 10L, title = "T1")
        val plan2 = plan(nodeId = 20L, title = "T2")

        val res = writer.savePlan(listOf(plan1, plan2))

        assertEquals(2, res.written)
        assertEquals(0, res.skipped)
        verify(exactly = 2) { repo.save(any()) }
    }

    @Test
    fun `savePlan - использует существующий id и не падает при ошибке сохранения`() {
        val repo = mockk<ChunkRepository>()
        val existing = sampleChunk(id = 777L, nodeId = 10L)

        every { repo.findTopByNodeIdOrderByCreatedAtDesc(10L) } returns existing
        every { repo.findTopByNodeIdOrderByCreatedAtDesc(20L) } returns null

        val savedSlot = slot<Chunk>()
        every { repo.save(capture(savedSlot)) } answers { firstArg() }
        every { repo.save(match { it.node.id == 20L }) } throws RuntimeException("db down")

        val writer = ChunkWriterImpl(repo)
        val res = writer.savePlan(listOf(plan(nodeId = 10L, title = "T1"), plan(nodeId = 20L, title = "T2")))

        assertEquals(1, res.written)
        assertEquals(0, res.skipped)

        val saved = savedSlot.captured
        assertEquals(777L, saved.id)
        assertNotNull(saved.pipeline["stages"])
        assertNotNull(saved.pipeline["params"])
        assertNotNull(saved.pipeline["service"])

        verify(exactly = 2) { repo.findTopByNodeIdOrderByCreatedAtDesc(any()) }
    }

    private fun plan(
        nodeId: Long,
        title: String,
    ): ChunkPlan {
        val app = Application(key = "app", name = "App")
        val node =
            Node(
                id = nodeId,
                application = app,
                fqn = "com.example.N$nodeId",
                name = "N$nodeId",
                kind = NodeKind.METHOD,
                lang = Lang.kotlin,
            )
        return ChunkPlan(
            id = "$nodeId:code:snippet",
            nodeId = nodeId,
            source = "code",
            kind = "snippet",
            lang = "kotlin",
            spanLines = null,
            title = title,
            sectionPath = listOf("com", "example"),
            relations = emptyList(),
            pipeline = PipelinePlan(stages = listOf("x"), params = mapOf("audience" to "coder"), service = ServiceMeta(strategy = "test")),
            node = node,
        )
    }

    private fun sampleChunk(
        id: Long,
        nodeId: Long,
    ): Chunk {
        val app = Application(key = "app", name = "App")
        val node =
            Node(
                id = nodeId,
                application = app,
                fqn = "com.example.N$nodeId",
                name = "N$nodeId",
                kind = NodeKind.METHOD,
                lang = Lang.kotlin,
            )
        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "code",
            content = "content",
            title = "T",
        )
    }
}

