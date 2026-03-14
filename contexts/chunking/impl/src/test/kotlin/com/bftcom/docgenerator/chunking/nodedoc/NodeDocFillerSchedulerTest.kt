package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class NodeDocFillerSchedulerTest {
    private lateinit var txManager: PlatformTransactionManager
    private lateinit var nodeRepo: NodeRepository
    private lateinit var generator: NodeDocGenerator
    private lateinit var scheduler: NodeDocFillerScheduler

    private val app = Application(key = "app1", name = "App1").apply { id = 1L }

    @BeforeEach
    fun setUp() {
        txManager = mockk {
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }
        nodeRepo = mockk(relaxed = true)
        generator = mockk(relaxed = true)

        scheduler =
            NodeDocFillerScheduler(
                txManager = txManager,
                nodeRepo = nodeRepo,
                generator = generator,
            )
    }

    private fun node(id: Long, kind: NodeKind = NodeKind.METHOD, fqn: String = "com.example.Node$id") =
        Node(application = app, fqn = fqn, kind = kind, lang = Lang.kotlin).apply { this.id = id }

    private fun doc(label: String = "") = NodeDocGenerator.GeneratedDoc(
        docTech = "tech $label", docPublic = "public $label", docDigest = "digest $label", modelMeta = emptyMap(),
    )

    // === poll() tests ===

    @Test
    fun `poll - processes ready nodes when available`() {
        val n = node(100L)
        every { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) } returns listOf(n)
        every { generator.generate(n, "ru", false) } returns doc()

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextAnyNodesWithoutDoc(any(), any()) }
        verify(exactly = 1) { generator.generate(n, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", any()) }
    }

    @Test
    fun `poll - falls through to cycle-break when ready query returns empty`() {
        val n = node(200L)
        every { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextAnyNodesWithoutDoc("ru", 10) } returns listOf(n)
        every { generator.generate(n, "ru", true) } returns doc()

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextAnyNodesWithoutDoc("ru", 10) }
        verify(exactly = 1) { generator.generate(n, "ru", true) }
        verify(exactly = 1) { generator.store(200L, "ru", any()) }
    }

    @Test
    fun `poll - does nothing when both queries return empty`() {
        every { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextAnyNodesWithoutDoc("ru", 10) } returns emptyList()

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextReadyNodesWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextAnyNodesWithoutDoc("ru", 10) }
        verify(exactly = 0) { generator.generate(any(), any(), any()) }
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    // === processBatch() tests ===

    @Test
    fun `processBatch - generates and stores for each node`() {
        val n1 = node(100L)
        val n2 = node(200L)
        val d1 = doc("1")
        val d2 = doc("2")

        every { generator.generate(n1, "ru", false) } returns d1
        every { generator.generate(n2, "ru", false) } returns d2

        scheduler.processBatch("READY", listOf(n1, n2), allowMissingDeps = false)

        verify(exactly = 1) { generator.generate(n1, "ru", false) }
        verify(exactly = 1) { generator.generate(n2, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", d1) }
        verify(exactly = 1) { generator.store(200L, "ru", d2) }
    }

    @Test
    fun `processBatch - passes allowMissingDeps to generator`() {
        val n = node(100L)
        every { generator.generate(n, "ru", true) } returns doc()

        scheduler.processBatch("CYCLE-BREAK", listOf(n), allowMissingDeps = true)

        verify(exactly = 1) { generator.generate(n, "ru", true) }
    }

    @Test
    fun `processBatch - skips node without id`() {
        val n = node(100L).apply { id = null }

        scheduler.processBatch("READY", listOf(n), allowMissingDeps = false)

        verify(exactly = 0) { generator.generate(any(), any(), any()) }
    }

    @Test
    fun `processBatch - skips node when generator returns null`() {
        val n = node(100L)
        every { generator.generate(n, "ru", false) } returns null

        scheduler.processBatch("READY", listOf(n), allowMissingDeps = false)

        verify(exactly = 1) { generator.generate(n, "ru", false) }
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - handles generation failure gracefully`() {
        val n1 = node(100L)
        val n2 = node(200L)
        val d2 = doc("2")

        every { generator.generate(n1, "ru", false) } throws RuntimeException("Generation failed")
        every { generator.generate(n2, "ru", false) } returns d2

        scheduler.processBatch("READY", listOf(n1, n2), allowMissingDeps = false)

        verify(exactly = 1) { generator.generate(n1, "ru", false) }
        verify(exactly = 1) { generator.generate(n2, "ru", false) }
        verify(exactly = 0) { generator.store(100L, any(), any()) }
        verify(exactly = 1) { generator.store(200L, "ru", d2) }
    }

    @Test
    fun `processBatch - handles empty batch`() {
        scheduler.processBatch("READY", emptyList(), allowMissingDeps = false)

        verify(exactly = 0) { generator.generate(any(), any(), any()) }
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - handles mixed success, skip, and failure`() {
        val n1 = node(100L)
        val n2 = node(200L)
        val n3 = node(300L)
        val d1 = doc("1")

        every { generator.generate(n1, "ru", false) } returns d1
        every { generator.generate(n2, "ru", false) } returns null
        every { generator.generate(n3, "ru", false) } throws RuntimeException("fail")

        scheduler.processBatch("READY", listOf(n1, n2, n3), allowMissingDeps = false)

        verify(exactly = 1) { generator.store(100L, "ru", d1) }
        verify(exactly = 0) { generator.store(200L, any(), any()) }
        verify(exactly = 0) { generator.store(300L, any(), any()) }
    }

    @Test
    fun `processBatch - processes large batch`() {
        val nodes = (1..50).map { i -> node(100L + i) }
        val docs = nodes.map { doc(it.id.toString()) }

        nodes.forEachIndexed { idx, n ->
            every { generator.generate(n, "ru", false) } returns docs[idx]
        }

        scheduler.processBatch("READY", nodes, allowMissingDeps = false)

        nodes.forEachIndexed { idx, n ->
            verify(exactly = 1) { generator.store(n.id!!, "ru", docs[idx]) }
        }
    }

    @Test
    fun `processBatch - handles batch where all fail`() {
        val n1 = node(100L)
        val n2 = node(200L)

        every { generator.generate(any(), "ru", false) } throws RuntimeException("fail")

        scheduler.processBatch("READY", listOf(n1, n2), allowMissingDeps = false)

        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - handles batch with different node kinds`() {
        val method = node(100L, NodeKind.METHOD)
        val cls = node(200L, NodeKind.CLASS, "com.example.Class1")
        val pkg = node(300L, NodeKind.PACKAGE, "com.example")
        val d1 = doc("method")
        val d2 = doc("class")
        val d3 = doc("package")

        every { generator.generate(method, "ru", true) } returns d1
        every { generator.generate(cls, "ru", true) } returns d2
        every { generator.generate(pkg, "ru", true) } returns d3

        scheduler.processBatch("CYCLE-BREAK", listOf(method, cls, pkg), allowMissingDeps = true)

        verify(exactly = 1) { generator.store(100L, "ru", d1) }
        verify(exactly = 1) { generator.store(200L, "ru", d2) }
        verify(exactly = 1) { generator.store(300L, "ru", d3) }
    }
}
