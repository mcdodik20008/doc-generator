package com.bftcom.docgenerator.graph

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.impl.KDocFetcherImpl
import com.bftcom.docgenerator.graph.impl.KotlinSourceWalker
import com.bftcom.docgenerator.graph.impl.KotlinToDomainVisitor
import com.bftcom.docgenerator.repo.ChunkRepository
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.file.Files
import java.nio.file.Path

class CrossFileResolutionTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `method call resolves across files by package simple name`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }

        Files.writeString(
            src.resolve("00_B.kt"),
            """
                package foo
                fun baz(){}
            """.trimIndent()
        )
        Files.writeString(
            src.resolve("10_A.kt"),
            """
                package foo
                class A { fun bar(){ baz() } }
            """.trimIndent()
        )

        val nodeRepo: NodeRepository = mock()
        val edgeRepo: EdgeRepository = mock()
        val chunkRepo: ChunkRepository = mock()

        val app = Application(id = 1L, key = "demo", name = "Demo")

        var seqId = 0L
        val nodesByFqn = linkedMapOf<String, Node>()

        // in-memory поведение save/findByApplicationIdAndFqn
        whenever(nodeRepo.save(any())).thenAnswer { inv ->
            val n = inv.arguments[0] as Node
            if (n.id == null) n.id = (++seqId)
            nodesByFqn[n.fqn] = n
            n
        }
        whenever(nodeRepo.findByApplicationIdAndFqn(eq(app.id!!), any())).thenAnswer { inv ->
            val fqn = inv.arguments[1] as String
            nodesByFqn[fqn]
        }
        whenever(edgeRepo.save(any())).thenAnswer { it.arguments[0] as Edge }
        whenever(chunkRepo.save(any())).thenAnswer { it.arguments[0] }

        val walker = KotlinSourceWalker(KDocFetcherImpl())
        val visitor = KotlinToDomainVisitor(app, nodeRepo, com.fasterxml.jackson.databind.ObjectMapper())

        walker.walk(src, visitor, emptyList())

        // Диагностика: узел foo.baz должен существовать
        val savedNodes = argumentCaptor<Node>().apply {
            verify(nodeRepo, atLeastOnce()).save(capture())
        }.allValues

        val bazNode = savedNodes.firstOrNull { it.fqn == "foo.baz" }
            ?: error(
                "Узел функции baz не создан. Сохранённые узлы:\n" +
                        savedNodes.joinToString("\n") { n -> "NODE ${n.id} ${n.fqn}" }
            )

        // Проверяем CALLS -> foo.baz
        val savedEdges = argumentCaptor<Edge>().apply {
            verify(edgeRepo, atLeastOnce()).save(capture())
        }.allValues

        val calls = savedEdges.filter { it.kind == EdgeKind.CALLS }
        if (calls.isEmpty()) {
            error(
                "Не найдено ни одного CALLS. Сохранённые edges:\n" +
                        savedEdges.joinToString("\n") { e -> "EDGE ${e.kind}: ${e.src.fqn} -> ${e.dst.fqn}" }
            )
        }

        assertTrue(
            calls.any { it.dst.fqn == "foo.baz" },
            buildString {
                appendLine("Ожидался хотя бы один Edge CALLS к foo.baz")
                appendLine("CALLS edges:")
                calls.forEach { e -> appendLine(" - ${e.src.fqn} -> ${e.dst.fqn}") }
            }
        )
    }
}
