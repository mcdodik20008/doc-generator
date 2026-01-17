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
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.OffsetDateTime

class ChunkBuildOrchestratorImplTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var strategies: Map<String, ChunkStrategy>
    private lateinit var chunkWriter: ChunkWriter
    private lateinit var runStore: ChunkRunStore
    private lateinit var orchestrator: ChunkBuildOrchestratorImpl

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk(relaxed = true)
        edgeRepo = mockk(relaxed = true)
        strategies = mockk(relaxed = true)
        chunkWriter = mockk(relaxed = true)
        runStore = mockk(relaxed = true)

        orchestrator = ChunkBuildOrchestratorImpl(
            nodeRepo = nodeRepo,
            edgeRepo = edgeRepo,
            strategies = strategies,
            chunkWriter = chunkWriter,
            runStore = runStore,
        )
    }

    @Test
    fun `start - выбрасывает исключение для неизвестной стратегии`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "unknown")
        every { strategies["unknown"] } returns null
        assertThrows<IllegalStateException> { orchestrator.start(req) }
    }

    @Test
    fun `start - обрабатывает пустой список узлов`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test-strategy")
        val run = ChunkRunStore.Run("run-1", OffsetDateTime.now())

        every { strategies["test-strategy"] } returns mockk(relaxed = true)
        every { runStore.create(1L, "test-strategy") } returns run
        every { nodeRepo.findAllByApplicationId(any(), any()) } returns emptyList()

        val result = orchestrator.start(req)
        assertThat(result.runId).isEqualTo("run-1")
        verify { runStore.markCompleted("run-1", 0, 0, 0) }
    }

    @Test
    fun `start - обрабатывает dryRun режим`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test-strategy", dryRun = true)
        val node = createNode(100L)
        val run = ChunkRunStore.Run("run-1", OffsetDateTime.now())
        val strategy = mockk<ChunkStrategy>(relaxed = true)

        every { strategies["test-strategy"] } returns strategy
        every { runStore.create(1L, "test-strategy") } returns run
        // Имитируем пагинацию: данные только на 0 странице
        every { nodeRepo.findAllByApplicationId(1L, any()) } answers {
            if (secondArg<PageRequest>().pageNumber == 0) listOf(node) else emptyList()
        }
        every { strategy.buildChunks(any(), any()) } returns listOf(createChunkPlan(node, "c", "s"))

        orchestrator.start(req)
        verify(exactly = 0) { chunkWriter.savePlan(any()) }
        verify { runStore.markCompleted("run-1", 1, 0, 1) }
    }

    @Test
    fun `start - обрабатывает limitNodes`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test-strategy", limitNodes = 1L)
        val node1 = createNode(101L)
        val node2 = createNode(102L)
        val run = ChunkRunStore.Run("run-1", OffsetDateTime.now())

        every { strategies["test-strategy"] } returns mockk(relaxed = true)
        every { runStore.create(1L, "test-strategy") } returns run
        every { nodeRepo.findAllByApplicationId(1L, any()) } returns listOf(node1, node2)

        orchestrator.start(req)
        // processedNodes должен быть 1 из-за лимита
        verify { runStore.markCompleted("run-1", 1, any(), any()) }
    }

    @Test
    fun `start - обрабатывает includeKinds фильтр`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test", includeKinds = setOf("CLASS"))
        val node = createNode(100L)

        every { strategies["test"] } returns mockk(relaxed = true)
        every { runStore.create(1L, "test") } returns ChunkRunStore.Run("r1", OffsetDateTime.now())
        // ВАЖНО: Тут PageImpl, так как метод репозитория возвращает Page
        every { nodeRepo.findPageAllByApplicationIdAndKindIn(1L, setOf(NodeKind.CLASS), any()) } answers {
            if (thirdArg<PageRequest>().pageNumber == 0) PageImpl(listOf(node)) else PageImpl(emptyList())
        }

        orchestrator.start(req)
        verify(atLeast = 1) { nodeRepo.findPageAllByApplicationIdAndKindIn(1L, any(), any()) }
    }

    @Test
    fun `start - обрабатывает withEdgesRelations = true`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test", withEdgesRelations = true)
        val node = createNode(100L)

        // Подготовка ребра
        val edge = mockk<Edge>()
        every { edge.src } returns node
        // node.id = 100L уже внутри createNode

        val strategy = mockk<ChunkStrategy>()
        every { strategy.buildChunks(any(), any()) } returns listOf(createChunkPlan(node, "c", "s"))

        every { strategies["test"] } returns strategy
        every { runStore.create(1L, "test") } returns ChunkRunStore.Run("r1", java.time.OffsetDateTime.now())

        // Пагинация
        every { nodeRepo.findAllByApplicationId(1L, any()) } answers {
            if (secondArg<org.springframework.data.domain.PageRequest>().pageNumber == 0) listOf(node) else emptyList()
        }

        // КЛЮЧЕВОЙ МОМЕНТ: Используем слот
        val idSlot = slot<Collection<Long>>()
        every { edgeRepo.findAllBySrcIdIn(capture(idSlot)) } returns listOf(edge)
        every { chunkWriter.savePlan(any()) } returns com.bftcom.docgenerator.chunking.api.ChunkWriter.SaveResult(1, 0)

        orchestrator.start(req)

        // Верифицируем через захваченные данные
        assertThat(idSlot.captured).contains(100L)
        verify(exactly = 1) { strategy.buildChunks(node, listOf(edge)) }
    }

    @Test
    fun `start - обрабатывает буфер планов размером больше или равно 1000`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test")
        val nodes = (1..1001).map { createNode(it.toLong()) }

        // Создаем мок стратегии и заставляем его возвращать план
        val strategy = mockk<ChunkStrategy>()
        // Для каждого вызова возвращаем список из одного плана
        every { strategy.buildChunks(any(), any()) } answers {
            val n = firstArg<Node>()
            listOf(createChunkPlan(n, "source", "kind"))
        }

        every { strategies["test"] } returns strategy
        every { runStore.create(1L, "test") } returns ChunkRunStore.Run("r1", OffsetDateTime.now())

        every { nodeRepo.findAllByApplicationId(1L, any()) } answers {
            if (secondArg<PageRequest>().pageNumber == 0) nodes else emptyList()
        }

        // Важно: возвращаем результат сохранения, чтобы счетчики в логах были верные
        every { chunkWriter.savePlan(any()) } returns ChunkWriter.SaveResult(1000, 0)

        orchestrator.start(req)

        // Теперь буфер наполнится:
        // 1. Сначала сработает триггер >= 1000 (на 1000-м узле)
        // 2. Потом финальный сброс остатка (1001-й узел)
        verify(atLeast = 2) { chunkWriter.savePlan(any()) }
    }

    @Test
    fun `start - обрабатывает ошибку и помечает run как failed`() {
        val req = ChunkBuildRequest(applicationId = 1L, strategy = "test")
        every { strategies["test"] } returns mockk(relaxed = true)
        every { runStore.create(1L, "test") } returns ChunkRunStore.Run("r1", OffsetDateTime.now())
        every { nodeRepo.findAllByApplicationId(any(), any()) } throws RuntimeException("Error")

        assertThrows<RuntimeException> { orchestrator.start(req) }
        verify { runStore.markFailed("r1", any()) }
    }

    private fun createNode(id: Long) = Node(
        application = Application(key = "app", name = "app").apply { this.id = 1L },
        fqn = "Node$id",
        kind = NodeKind.CLASS,
        lang = Lang.kotlin
    ).apply { this.id = id }

    private fun createChunkPlan(
        node: Node,
        source: String,
        kind: String
    ): ChunkPlan = ChunkPlan(
        id = "${node.id}:$source:$kind",
        nodeId = node.id ?: 0L,
        source = source,
        kind = kind,
        lang = "kotlin",
        title = node.fqn,
        node = node,
        spanLines = null,
        sectionPath = emptyList(),
        relations = emptyList(),
        pipeline = PipelinePlan(
            stages = emptyList(),
            params = emptyMap(),
            service = ServiceMeta(
                strategy = "per-node",
                priority = 0
            )
        )
    )
}