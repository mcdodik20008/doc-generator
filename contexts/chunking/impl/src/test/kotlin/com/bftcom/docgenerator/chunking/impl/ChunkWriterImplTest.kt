package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.chunking.model.plan.PipelinePlan
import com.bftcom.docgenerator.chunking.model.plan.RelationHint
import com.bftcom.docgenerator.chunking.model.plan.ServiceMeta
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkWriterImplTest {
    private lateinit var chunkRepo: ChunkRepository
    private lateinit var writer: ChunkWriterImpl
    private val app = Application(id = 1L, key = "app", name = "App")

    @BeforeEach
    fun setUp() {
        chunkRepo = mockk(relaxed = true)
        writer = ChunkWriterImpl(chunkRepo)
    }

    @Test
    fun `savePlan - сохраняет новый chunk когда existing null`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(100L) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        val result = writer.savePlan(listOf(plan))

        // then
        assertThat(result.written).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        verify(exactly = 1) { chunkRepo.save(any()) }
    }

    @Test
    fun `savePlan - обновляет существующий chunk когда existing не null`() {
        // given
        val existing = Chunk(
            id = 1L,
            application = app,
            node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS),
            source = "code",
            kind = "snippet",
            content = "old content",
        )

        val node = existing.node
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(100L) } returns existing
        every { chunkRepo.save(any()) } returns existing

        // when
        val result = writer.savePlan(listOf(plan))

        // then
        assertThat(result.written).isEqualTo(1)
        verify(exactly = 1) { chunkRepo.save(any()) }
        verify { chunkRepo.save(match { it.id == 1L }) }
    }

    @Test
    fun `savePlan - использует sourceCode для content`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify { chunkRepo.save(match { it.content == "fun test() {}" }) }
    }

    @Test
    fun `savePlan - использует docComment когда sourceCode null`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = null
        node.docComment = "/** Test doc */"

        val plan = plan(node, source = "doc", kind = "explanation")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify { chunkRepo.save(match { it.content == "/** Test doc */" }) }
    }

    @Test
    fun `savePlan - использует empty когда sourceCode и docComment null`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = null
        node.docComment = null

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify { chunkRepo.save(match { it.content == "(empty)" }) }
    }

    @Test
    fun `savePlan - обрезает пробелы из content`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = "  fun test() {}  "

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify { chunkRepo.save(match { it.content == "fun test() {}" }) }
    }

    @Test
    fun `savePlan - использует blank как empty когда trimmed пустой`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = "   "

        val plan = plan(node, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify { chunkRepo.save(match { it.content == "(empty)" }) }
    }

    @Test
    fun `savePlan - использует lang из plan или ru по умолчанию`() {
        // given
        val node1 = node(fqn = "com.example.Test1", name = "Test1", packageName = "com.example", kind = NodeKind.CLASS)
        node1.id = 100L
        val plan1 = plan(node1, source = "code", kind = "snippet", lang = "kotlin")

        val node2 = node(fqn = "com.example.Test2", name = "Test2", packageName = "com.example", kind = NodeKind.CLASS)
        node2.id = 200L
        val plan2 = plan(node2, source = "doc", kind = "explanation", lang = null)

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan1, plan2))

        // then
        verify { chunkRepo.save(match { it.langDetected == "kotlin" }) }
        verify { chunkRepo.save(match { it.langDetected == "ru" }) }
    }

    @Test
    fun `savePlan - обрабатывает ошибки при сохранении`() {
        // given
        val node1 = node(fqn = "com.example.Test1", name = "Test1", packageName = "com.example", kind = NodeKind.CLASS)
        node1.id = 100L
        val plan1 = plan(node1, source = "code", kind = "snippet")

        val node2 = node(fqn = "com.example.Test2", name = "Test2", packageName = "com.example", kind = NodeKind.CLASS)
        node2.id = 200L
        val plan2 = plan(node2, source = "code", kind = "snippet")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(100L) } returns null
        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(200L) } returns null
        every { chunkRepo.save(match { (it as Chunk).node?.id == 100L }) } returns mockk<Chunk>()
        every { chunkRepo.save(match { (it as Chunk).node?.id == 200L }) } throws RuntimeException("Save failed")

        // when
        val result = writer.savePlan(listOf(plan1, plan2))

        // then
        assertThat(result.written).isEqualTo(1) // только один сохранен успешно
        assertThat(result.skipped).isEqualTo(0)
        verify(exactly = 2) { chunkRepo.save(any()) }
    }

    @Test
    fun `savePlan - сохраняет несколько планов`() {
        // given
        val plans = (1..5).map { i ->
            val node = node(fqn = "com.example.Test$i", name = "Test$i", packageName = "com.example", kind = NodeKind.CLASS)
            node.id = (100 + i).toLong()
            plan(node, source = "code", kind = "snippet")
        }

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        val result = writer.savePlan(plans)

        // then
        assertThat(result.written).isEqualTo(5)
        verify(exactly = 5) { chunkRepo.save(any()) }
    }

    @Test
    fun `savePlan - устанавливает правильные поля chunk`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val plan = plan(node, source = "code", kind = "snippet", lang = "kotlin")

        every { chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(any()) } returns null
        every { chunkRepo.save(any()) } returns mockk<Chunk>()

        // when
        writer.savePlan(listOf(plan))

        // then
        verify {
            chunkRepo.save(
                match { chunk ->
                    chunk.application == app &&
                        chunk.node == node &&
                        chunk.source == "code" &&
                        chunk.kind == "snippet" &&
                        chunk.langDetected == "kotlin" &&
                        chunk.contentHash == null &&
                        chunk.tokenCount == null &&
                        chunk.emb == null &&
                        chunk.embedModel == null &&
                        chunk.embedTs == null
                },
            )
        }
    }

    private fun node(
        fqn: String,
        name: String,
        packageName: String,
        kind: NodeKind,
    ): Node =
        Node(
            id = null,
            application = app,
            fqn = fqn,
            name = name,
            packageName = packageName,
            kind = kind,
            lang = Lang.kotlin,
        )

    private fun plan(
        node: Node,
        source: String,
        kind: String,
        lang: String? = "kotlin",
    ): ChunkPlan =
        ChunkPlan(
            id = "${node.id}:$source:$kind",
            nodeId = node.id!!,
            source = source,
            kind = kind,
            lang = lang,
            spanLines = null,
            title = node.fqn,
            sectionPath = emptyList(),
            relations = emptyList(),
            pipeline = PipelinePlan(
                stages = emptyList(),
                params = emptyMap(),
                service = ServiceMeta(strategy = "per-node", priority = 0),
            ),
            node = node,
        )
}
