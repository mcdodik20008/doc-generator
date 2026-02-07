package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PerNodeChunkStrategyTest {
    private val app = Application(id = 1L, key = "app", name = "App")
    private val strategy = PerNodeChunkStrategy()

    @Test
    fun `buildChunks - создает DOC-чанк когда есть signature`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.signature = "fun test(): Unit"

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans).hasSize(1)
        val plan = plans[0]
        assertThat(plan.source).isEqualTo("doc")
        assertThat(plan.kind).isEqualTo("tech")
        assertThat(plan.lang).isEqualTo("ru")
        assertThat(plan.pipeline.stages).containsExactly("render-doc", "embed", "link-edges")
        assertThat(plan.pipeline.params["signature"]).isEqualTo("fun test(): Unit")
        assertThat(plan.pipeline.params["hasDocComment"]).isEqualTo(false)
    }

    @Test
    fun `buildChunks - создает DOC-чанк когда есть docComment`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.docComment = "/** Test documentation */"

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans).hasSize(1)
        val plan = plans[0]
        assertThat(plan.source).isEqualTo("doc")
        assertThat(plan.pipeline.params["hasDocComment"]).isEqualTo(true)
    }

    @Test
    fun `buildChunks - создает CODE-чанк когда нет документации`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.signature = null
        node.docComment = null

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans).hasSize(1)
        val plan = plans[0]
        assertThat(plan.source).isEqualTo("code")
        assertThat(plan.kind).isEqualTo("snippet")
        assertThat(plan.lang).isEqualTo("kotlin")
        assertThat(plan.pipeline.stages).containsExactly("extract-snippet", "summarize", "embed", "link-edges")
    }

    @Test
    fun `buildChunks - sectionPath содержит packageName и name`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].sectionPath).containsExactly("com", "example", "Test")
    }

    @Test
    fun `buildChunks - sectionPath без packageName содержит только name`() {
        // given
        val node = node(fqn = "Test", name = "Test", packageName = null, kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].sectionPath).containsExactly("Test")
    }

    @Test
    fun `buildChunks - sectionPath без name содержит только packageName`() {
        // given
        val node = node(fqn = "com.example.Test", name = null, packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].sectionPath).containsExactly("com", "example")
    }

    @Test
    fun `buildChunks - sectionPath пустой когда нет packageName и name`() {
        // given
        val node = node(fqn = "Test", name = null, packageName = null, kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].sectionPath).isEmpty()
    }

    @Test
    fun `buildChunks - фильтрует edges по CALLS`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L

        val target1 = node(fqn = "com.example.Target1", name = "Target1", packageName = "com.example", kind = NodeKind.METHOD)
        target1.id = 200L
        val target2 = node(fqn = "com.example.Target2", name = "Target2", packageName = "com.example", kind = NodeKind.CLASS)
        target2.id = 300L

        val edges = listOf(
            Edge(src = node, dst = target1, kind = EdgeKind.CALLS),
            Edge(src = node, dst = target2, kind = EdgeKind.DEPENDS_ON), // не CALLS
        )

        // when
        val plans = strategy.buildChunks(node, edges)

        // then
        assertThat(plans[0].relations).hasSize(1)
        assertThat(plans[0].relations[0].kind).isEqualTo("CALLS")
        assertThat(plans[0].relations[0].dstNodeId).isEqualTo(200L)
    }

    @Test
    fun `buildChunks - relations пустой когда нет CALLS edges`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L

        val target = node(fqn = "com.example.Target", name = "Target", packageName = "com.example", kind = NodeKind.CLASS)
        target.id = 200L

        val edges = listOf(Edge(src = node, dst = target, kind = EdgeKind.DEPENDS_ON))

        // when
        val plans = strategy.buildChunks(node, edges)

        // then
        assertThat(plans[0].relations).isEmpty()
    }

    @Test
    fun `buildChunks - spanLines создается когда есть lineStart и lineEnd`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L
        node.lineStart = 10
        node.lineEnd = 20

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].spanLines).isEqualTo(10..20)
    }

    @Test
    fun `buildChunks - spanLines null когда нет lineStart`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L
        node.lineStart = null
        node.lineEnd = 20

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].spanLines).isNull()
    }

    @Test
    fun `buildChunks - spanLines null когда нет lineEnd`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L
        node.lineStart = 10
        node.lineEnd = null

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].spanLines).isNull()
    }

    @Test
    fun `buildChunks - spanLines инвертируется когда lineStart больше lineEnd`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L
        node.lineStart = 20
        node.lineEnd = 10

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].spanLines).isEqualTo(10..20) // инвертировано через minOf/maxOf
    }

    @Test
    fun `buildChunks - priority 10 для ENDPOINT`() {
        // given
        val node = node(fqn = "com.example.Endpoint", name = "Endpoint", packageName = "com.example", kind = NodeKind.ENDPOINT)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].pipeline.service.priority).isEqualTo(10)
    }

    @Test
    fun `buildChunks - priority 10 для METHOD`() {
        // given
        val node = node(fqn = "com.example.Method", name = "Method", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].pipeline.service.priority).isEqualTo(10)
    }

    @Test
    fun `buildChunks - priority 5 для CLASS`() {
        // given
        val node = node(fqn = "com.example.Class", name = "Class", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].pipeline.service.priority).isEqualTo(5)
    }

    @Test
    fun `buildChunks - priority 0 для других типов`() {
        // given
        val node = node(fqn = "com.example.Field", name = "Field", packageName = "com.example", kind = NodeKind.FIELD)
        node.id = 100L

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].pipeline.service.priority).isEqualTo(0)
    }

    @Test
    fun `buildChunks - CODE-чанк содержит filePath и hasSourceInNode`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.filePath = "/path/to/file.kt"
        node.sourceCode = "fun test() {}"

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].source).isEqualTo("code")
        assertThat(plans[0].pipeline.params["filePath"]).isEqualTo("/path/to/file.kt")
        assertThat(plans[0].pipeline.params["hasSourceInNode"]).isEqualTo(true)
    }

    @Test
    fun `buildChunks - CODE-чанк с null sourceCode имеет hasSourceInNode = false`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.sourceCode = null

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].pipeline.params["hasSourceInNode"]).isEqualTo(false)
    }

    @Test
    fun `buildChunks - DOC-чанк с пустым docComment имеет hasDocComment = false`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.signature = "fun test(): Unit" // есть signature, поэтому hasDoc = true
        node.docComment = "" // пустой docComment

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].source).isEqualTo("doc")
        assertThat(plans[0].pipeline.params["hasDocComment"]).isEqualTo(false)
    }

    @Test
    fun `buildChunks - DOC-чанк с blank docComment имеет hasDocComment = false`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L
        node.signature = "fun test(): Unit" // есть signature, поэтому hasDoc = true
        node.docComment = "   " // blank docComment

        // when
        val plans = strategy.buildChunks(node, emptyList())

        // then
        assertThat(plans[0].source).isEqualTo("doc")
        assertThat(plans[0].pipeline.params["hasDocComment"]).isEqualTo(false)
    }

    @Test
    fun `buildChunks - relations обрабатывает edge с null dst id`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.METHOD)
        node.id = 100L

        val target = node(fqn = "com.example.Target", name = "Target", packageName = "com.example", kind = NodeKind.METHOD)
        target.id = null // null id

        val edges = listOf(Edge(src = node, dst = target, kind = EdgeKind.CALLS))

        // when
        val plans = strategy.buildChunks(node, edges)

        // then
        assertThat(plans[0].relations).hasSize(1)
        assertThat(plans[0].relations[0].dstNodeId).isEqualTo(-1) // fallback для null id
    }

    @Test
    fun `buildChunks - chunkId формируется корректно`() {
        // given
        val node = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        node.id = 100L

        // when
        val nodeWithDoc = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        nodeWithDoc.id = 100L
        nodeWithDoc.signature = "test"
        
        val nodeWithoutDoc = node(fqn = "com.example.Test", name = "Test", packageName = "com.example", kind = NodeKind.CLASS)
        nodeWithoutDoc.id = 100L
        
        val docPlans = strategy.buildChunks(nodeWithDoc, emptyList())
        val codePlans = strategy.buildChunks(nodeWithoutDoc, emptyList())

        // then
        assertThat(docPlans[0].id).isEqualTo("100:doc:tech")
        assertThat(codePlans[0].id).isEqualTo("100:code:snippet")
    }

    private fun node(
        fqn: String,
        name: String?,
        packageName: String?,
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
}
