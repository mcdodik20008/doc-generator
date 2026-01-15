package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerNodeChunkStrategyTest {
    private val strategy = PerNodeChunkStrategy()

    @Test
    fun `buildChunks - строит doc chunk когда есть signature`() {
        val app = Application(key = "app", name = "App")
        val node = Node(
            id = 1L,
            application = app,
            fqn = "com.example.Foo.bar",
            name = "bar",
            packageName = "com.example",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            signature = "fun bar(x: Int): Int",
            docComment = null,
            lineStart = 10,
            lineEnd = 5, // проверяем нормализацию диапазона
        )

        val dst = Node(
            id = 2L,
            application = app,
            fqn = "com.example.Baz.qux",
            name = "qux",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )

        val edges = listOf(
            Edge(src = node, dst = dst, kind = EdgeKind.CALLS),
            Edge(src = node, dst = dst, kind = EdgeKind.CALLS_CODE),
        )

        val plans = strategy.buildChunks(node, edges)

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("doc", plan.source)
        assertEquals("explanation", plan.kind)
        assertEquals("ru", plan.lang)
        assertEquals("1:doc:explanation", plan.id)
        assertNotNull(plan.spanLines)
        assertEquals(5..10, plan.spanLines)
        assertTrue(plan.relations.all { it.kind == "CALLS" }) // только EdgeKind.CALLS
        assertEquals(1, plan.relations.size)
        assertEquals(2L, plan.relations.single().dstNodeId)
        assertEquals("per-node", plan.pipeline.service.strategy)
    }

    @Test
    fun `buildChunks - строит code chunk когда нет signature и docComment`() {
        val app = Application(key = "app", name = "App")
        val node = Node(
            id = 1L,
            application = app,
            fqn = "com.example.Foo",
            name = "Foo",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            signature = null,
            docComment = null,
            lineStart = null,
            lineEnd = null,
            filePath = "src/main/kotlin/com/example/Foo.kt",
            sourceCode = null,
        )

        val plans = strategy.buildChunks(node, emptyList())

        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("code", plan.source)
        assertEquals("snippet", plan.kind)
        assertEquals("kotlin", plan.lang)
        assertEquals("1:code:snippet", plan.id)
        assertNull(plan.spanLines)
        assertEquals(0, plan.relations.size)
    }
}

