package com.bftcom.docgenerator.graph

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
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

class CrossFileClassToClassTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `class A creates B and calls B ping`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }

        // Файл с классом B — индексируется первым
        Files.writeString(
            src.resolve("00_B.kt"),
            """
                package foo
                class B {
                    fun ping() {}
                }
            """.trimIndent()
        )
        // Файл с классом A — создаёт B() и вызывает b.ping()
        Files.writeString(
            src.resolve("10_A.kt"),
            """
                package foo
                class A {
                    fun makeAndUse() {
                        val b = B()
                        b.ping()
                    }
                }
            """.trimIndent()
        )

        // --- Моки + in-memory NodeRepository ---
        val nodeRepo: NodeRepository = mock()
        val edgeRepo: EdgeRepository = mock()
        val chunkRepo: ChunkRepository = mock()

        val app = Application(id = 1L, key = "demo", name = "Demo")

        var seqId = 0L
        val nodesByFqn = linkedMapOf<String, Node>()

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

        val walker = KotlinSourceWalker()
        val visitor = KotlinToDomainVisitor(app, nodeRepo, edgeRepo)

        walker.walk(src, visitor)

        // --- Узлы, которые должны существовать ---
        val savedNodes = argumentCaptor<Node>().apply {
            verify(nodeRepo, atLeastOnce()).save(capture())
        }.allValues

        val aClass   = savedNodes.firstOrNull { it.fqn == "foo.A" } ?: error("Не создан узел класса foo.A")
        val bClass   = savedNodes.firstOrNull { it.fqn == "foo.B" } ?: error("Не создан узел класса foo.B")
        val aMethod  = savedNodes.firstOrNull { it.fqn == "foo.A.makeAndUse" } ?: error("Не создан узел метода foo.A.makeAndUse")
        val bPingFun = savedNodes.firstOrNull { it.fqn == "foo.B.ping" } ?: error("Не создан узел метода foo.B.ping")

        // --- Рёбра ---
        val savedEdges = argumentCaptor<Edge>().apply {
            verify(edgeRepo, atLeastOnce()).save(capture())
        }.allValues

        // 1) Конструктор (как минимум один из вариантов должен быть)
        val constructorEdge = savedEdges.any {
            it.kind == EdgeKind.CALLS && it.src.fqn == aMethod.fqn &&
                    (it.dst.fqn == bClass.fqn || it.dst.fqn.endsWith(".<init>") || it.dst.fqn.startsWith("foo.B"))
        }
        assertTrue(
            constructorEdge,
            buildString {
                appendLine("Ожидался вызов конструктора B() из foo.A.makeAndUse")
                appendLine(savedEdges.joinToString("\n") { e -> " - ${e.kind}: ${e.src.fqn} -> ${e.dst.fqn}" })
            }
        )

        // 2) Вызов метода B.ping
        val callsPing = savedEdges.any {
            it.kind == EdgeKind.CALLS && it.src.fqn == aMethod.fqn && it.dst.fqn == bPingFun.fqn
        }
        assertTrue(
            callsPing,
            buildString {
                appendLine("Нет CALLS foo.A.makeAndUse -> foo.B.ping")
                appendLine("Edges:")
                savedEdges.forEach { e -> appendLine(" - ${e.kind}: ${e.src.fqn} -> ${e.dst.fqn}") }
            }
        )
    }
}
