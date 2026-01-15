package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkRunStore
import com.bftcom.docgenerator.chunking.api.ChunkStrategy
import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.dto.ChunkBuildRequest
import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.chunking.model.plan.PipelinePlan
import com.bftcom.docgenerator.chunking.model.plan.ServiceMeta
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkBuildOrchestratorImplTest {

    private fun node(app: Application, id: Long, kind: NodeKind = NodeKind.METHOD): Node =
        Node(
            id = id,
            application = app,
            fqn = "com.example.N$id",
            name = "N$id",
            kind = kind,
            lang = Lang.kotlin,
        )

    private fun planFor(node: Node, idx: Int = 0): ChunkPlan =
        ChunkPlan(
            id = "${node.id}:code:snippet:$idx",
            nodeId = node.id!!,
            source = "code",
            kind = "snippet",
            lang = node.lang.name.lowercase(),
            spanLines = null,
            title = node.fqn,
            sectionPath = listOf("com", "example"),
            relations = emptyList(),
            pipeline = PipelinePlan(stages = listOf("x"), service = ServiceMeta(strategy = "test")),
            node = node,
        )

    @Test
    fun `start - dryRun не вызывает writer, но считает skipped`() {
        val app = Application(key = "app", name = "App")
        val nodes = listOf(node(app, 1), node(app, 2))

        val nodeRepo = mockk<NodeRepository>()
        every { nodeRepo.findAllByApplicationId(eq(1L), any()) } answers {
            val pageable = secondArg<org.springframework.data.domain.Pageable>()
            if (pageable.pageNumber == 0) nodes else emptyList()
        }

        val edgeRepo = mockk<EdgeRepository>(relaxed = true)

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } answers {
            val n = firstArg<Node>()
            listOf(planFor(n))
        }

        val writer = mockk<ChunkWriter>(relaxed = true)
        val runStore = mockk<ChunkRunStore>(relaxed = true)
        every { runStore.create(1L, "per-node") } returns
            ChunkRunStore.Run(runId = "r1", startedAt = OffsetDateTime.now())

        val orchestrator =
            ChunkBuildOrchestratorImpl(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                strategies = mapOf("per-node" to strategy),
                chunkWriter = writer,
                runStore = runStore,
            )

        val req =
            ChunkBuildRequest(
                applicationId = 1L,
                strategy = "per-node",
                dryRun = true,
                withEdgesRelations = false,
                batchSize = 50,
            )

        orchestrator.start(req)

        verify(exactly = 0) { writer.savePlan(any()) }
        verify { runStore.markCompleted("r1", processed = 2, written = 0, skipped = 2) }
    }

    @Test
    fun `start - includeKinds парсит NodeKind и игнорирует неизвестные`() {
        val app = Application(key = "app", name = "App")
        val nodes = listOf(node(app, 1, NodeKind.METHOD))

        val kindsSlot: CapturingSlot<Set<NodeKind>> = slot()

        val nodeRepo = mockk<NodeRepository>()
        every { nodeRepo.findPageAllByApplicationIdAndKindIn(eq(1L), capture(kindsSlot), any()) } answers {
            val pageable = thirdArg<org.springframework.data.domain.Pageable>()
            if (pageable.pageNumber == 0) PageImpl(nodes) else PageImpl(emptyList())
        }

        val edgeRepo = mockk<EdgeRepository>(relaxed = true)

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } answers {
            val n = firstArg<Node>()
            listOf(planFor(n))
        }

        val writer = mockk<ChunkWriter>()
        every { writer.savePlan(any()) } returns ChunkWriter.SaveResult(written = 1, skipped = 0)

        val runStore = mockk<ChunkRunStore>(relaxed = true)
        every { runStore.create(1L, "per-node") } returns
            ChunkRunStore.Run(runId = "r1", startedAt = OffsetDateTime.now())

        val orchestrator =
            ChunkBuildOrchestratorImpl(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                strategies = mapOf("per-node" to strategy),
                chunkWriter = writer,
                runStore = runStore,
            )

        val req =
            ChunkBuildRequest(
                applicationId = 1L,
                strategy = "per-node",
                includeKinds = setOf("method", "no_such_kind"),
                withEdgesRelations = false,
                batchSize = 50,
            )

        orchestrator.start(req)

        assertEquals(setOf(NodeKind.METHOD), kindsSlot.captured)
        verify { runStore.markCompleted("r1", processed = 1, written = 1, skipped = 0) }
    }

    @Test
    fun `start - withEdgesRelations подтягивает рёбра батчем и передает их стратегии`() {
        val app = Application(key = "app", name = "App")
        val n1 = node(app, 1, NodeKind.METHOD)
        val n2 = node(app, 2, NodeKind.METHOD)
        val nodes = listOf(n1, n2)

        val nodeRepo = mockk<NodeRepository>()
        every { nodeRepo.findAllByApplicationId(eq(1L), any()) } answers {
            val pageable = secondArg<org.springframework.data.domain.Pageable>()
            if (pageable.pageNumber == 0) nodes else emptyList()
        }

        val edgeRepo = mockk<EdgeRepository>()
        val e = Edge(src = n1, dst = n2, kind = EdgeKind.CALLS_CODE)
        every { edgeRepo.findAllBySrcIdIn(any<Collection<Long>>()) } returns listOf(e)

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } returns emptyList()

        val writer = mockk<ChunkWriter>(relaxed = true)
        val runStore = mockk<ChunkRunStore>(relaxed = true)
        every { runStore.create(1L, "per-node") } returns
            ChunkRunStore.Run(runId = "r1", startedAt = OffsetDateTime.now())

        val orchestrator =
            ChunkBuildOrchestratorImpl(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                strategies = mapOf("per-node" to strategy),
                chunkWriter = writer,
                runStore = runStore,
            )

        val req =
            ChunkBuildRequest(
                applicationId = 1L,
                strategy = "per-node",
                withEdgesRelations = true,
                batchSize = 50,
            )

        orchestrator.start(req)

        val idsSlot = slot<Collection<Long>>()
        verify(exactly = 1) { edgeRepo.findAllBySrcIdIn(capture(idsSlot)) }
        assertTrue(idsSlot.captured.containsAll(listOf(1L, 2L)))
        verify { strategy.buildChunks(n1, match { it.size == 1 && it[0].kind == EdgeKind.CALLS_CODE }) }
        verify { strategy.buildChunks(n2, match { it.isEmpty() }) }
    }

    @Test
    fun `start - flush буфера на 1000 планов`() {
        val app = Application(key = "app", name = "App")
        val nodes = (1L..1001L).map { node(app, it, NodeKind.METHOD) }

        val nodeRepo = mockk<NodeRepository>()
        every { nodeRepo.findAllByApplicationId(eq(1L), any()) } answers {
            val pageable = secondArg<org.springframework.data.domain.Pageable>()
            if (pageable.pageNumber == 0) nodes else emptyList()
        }

        val edgeRepo = mockk<EdgeRepository>(relaxed = true)

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } answers {
            val n = firstArg<Node>()
            listOf(planFor(n))
        }

        val writer = mockk<ChunkWriter>()
        every { writer.savePlan(match { it.size == 1000 }) } returns ChunkWriter.SaveResult(written = 1000, skipped = 0)
        every { writer.savePlan(match { it.size == 1 }) } returns ChunkWriter.SaveResult(written = 1, skipped = 0)

        val runStore = mockk<ChunkRunStore>(relaxed = true)
        every { runStore.create(1L, "per-node") } returns
            ChunkRunStore.Run(runId = "r1", startedAt = OffsetDateTime.now())

        val orchestrator =
            ChunkBuildOrchestratorImpl(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                strategies = mapOf("per-node" to strategy),
                chunkWriter = writer,
                runStore = runStore,
            )

        val req =
            ChunkBuildRequest(
                applicationId = 1L,
                strategy = "per-node",
                withEdgesRelations = false,
                batchSize = 2000,
            )

        orchestrator.start(req)

        verify(exactly = 2) { writer.savePlan(any()) }
        verify { runStore.markCompleted("r1", processed = 1001, written = 1001, skipped = 0) }
    }

    @Test
    fun `start - limitNodes ограничивает обработку`() {
        val app = Application(key = "app", name = "App")
        val nodes = (1L..10L).map { node(app, it, NodeKind.METHOD) }

        val nodeRepo = mockk<NodeRepository>()
        every { nodeRepo.findAllByApplicationId(eq(1L), any()) } answers {
            val pageable = secondArg<org.springframework.data.domain.Pageable>()
            if (pageable.pageNumber == 0) nodes else emptyList()
        }

        val edgeRepo = mockk<EdgeRepository>(relaxed = true)

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } answers {
            val n = firstArg<Node>()
            listOf(planFor(n))
        }

        val writer = mockk<ChunkWriter>()
        every { writer.savePlan(any()) } returns ChunkWriter.SaveResult(written = 3, skipped = 0)

        val runStore = mockk<ChunkRunStore>(relaxed = true)
        every { runStore.create(1L, "per-node") } returns
            ChunkRunStore.Run(runId = "r1", startedAt = OffsetDateTime.now())

        val orchestrator =
            ChunkBuildOrchestratorImpl(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                strategies = mapOf("per-node" to strategy),
                chunkWriter = writer,
                runStore = runStore,
            )

        val req =
            ChunkBuildRequest(
                applicationId = 1L,
                strategy = "per-node",
                limitNodes = 3,
                withEdgesRelations = false,
                batchSize = 50,
            )

        orchestrator.start(req)

        verify(exactly = 3) { strategy.buildChunks(any(), any()) }
        verify { runStore.markCompleted("r1", processed = 3, written = 3, skipped = 0) }
    }
}

