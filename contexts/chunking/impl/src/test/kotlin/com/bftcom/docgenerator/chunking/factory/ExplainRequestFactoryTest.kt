package com.bftcom.docgenerator.chunking.factory

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExplainRequestFactoryTest {

    @Test
    fun `toCoderExplainRequest - обрезает codeExcerpt и hints`() {
        val app = Application(key = "app", name = "App")
        val bigCode = buildString {
            repeat(4000) { append("line ").append(it).append('\n') }
        }

        val hugeModifiers =
            (1..400).associate { i ->
                "m$i" to "x".repeat(12)
            }

        val node = Node(
            id = 1L,
            application = app,
            fqn = "com.example.Foo.bar",
            name = "bar",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            sourceCode = bigCode,
            meta = mapOf(
                "pkgFqn" to "com.example",
                "modifiers" to hugeModifiers,
            ),
        )

        val chunk = Chunk(
            id = 10L,
            application = app,
            node = node,
            source = "code",
            content = "content",
            title = "Foo.bar",
            pipeline = mapOf("params" to mapOf("strategy" to "per-node", "audience" to "coder")),
        )

        val req =
            with(ExplainRequestFactory) {
                chunk.toCoderExplainRequest()
            }

        assertEquals("Foo.bar", req.nodeFqn)
        assertEquals("kotlin", req.language)
        assertNotNull(req.codeExcerpt)
        assertContains(req.codeExcerpt, "[код обрезан")

        assertNotNull(req.hints)
        assertTrue(req.hints!!.length <= 2100) // лимит 2000 + небольшой хвост с сообщением
        assertContains(req.hints!!, "[подсказки обрезаны")
    }

    @Test
    fun `toTalkerRewriteRequest - бросает если contentRaw пуст`() {
        val app = Application(key = "app", name = "App")
        val node = Node(
            id = 1L,
            application = app,
            fqn = "com.example.Foo",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            sourceCode = "class Foo",
        )

        val chunk = Chunk(
            id = 10L,
            application = app,
            node = node,
            source = "doc",
            content = "content",
            contentRaw = null,
        )

        assertFailsWith<RuntimeException> {
            with(ExplainRequestFactory) {
                chunk.toTalkerRewriteRequest()
            }
        }
    }
}

