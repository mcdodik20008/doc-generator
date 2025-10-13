package com.bftcom.docgenerator.graph

import org.junit.jupiter.api.Assertions.assertTrue
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.SourceVisitor
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import com.bftcom.docgenerator.graph.impl.KotlinSourceWalker

class KotlinSourceWalkerTest {
    @TempDir
    lateinit var temp: Path


    @Test
    fun `walk simple kotlin file and emit callbacks`() {
        val src = temp.resolve("src").also { Files.createDirectories(it) }
        val file = src.resolve("A.kt")
        Files.writeString(
            file, """
                package foo
                
                class A: Runnable {
                    val x: Int = 1
                    fun bar(p: String) {
                    println("hi")
                    baz()
                    }
                }
                fun baz() {}
        """.trimIndent())

        val walker = KotlinSourceWalker()
        val calls = mutableListOf<String>()
        val v = object : SourceVisitor {
            override fun onPackage(pkgFqn: String, filePath: String) {
                calls += "pkg:$pkgFqn"
            }

            override fun onType(
                kind: NodeKind,
                fqn: String,
                pkgFqn: String,
                name: String,
                filePath: String,
                spanLines: IntRange,
                supertypesSimple: List<String>
            ) {
                calls += "type:${kind}:${fqn}:${supertypesSimple.joinToString()}"
            }

            override fun onField(ownerFqn: String, name: String, filePath: String, spanLines: IntRange) {
                calls += "field:$ownerFqn.$name"
            }

            override fun onFunction(
                ownerFqn: String?,
                name: String,
                paramNames: List<String>,
                filePath: String,
                spanLines: IntRange,
                callsSimple: List<String>
            ) {
                calls += "fun:${ownerFqn ?: "<top>"}.$name(${paramNames.size}) calls=${callsSimple.joinToString()}"
            }
        }

        walker.walk(src, v)

        // Assertions (упрощённо, важные факты)
        assertTrue(calls.any { it.startsWith("pkg:foo") })
        assertTrue(calls.any { it.startsWith("type:CLASS:foo.A") })
        assertTrue(calls.any { it.contains("Runnable") })
        assertTrue(calls.any { it == "field:foo.A.x" })
        assertTrue(calls.any { it.contains("fun:foo.A.bar(1)") && it.contains("println") && it.contains("baz") })
        assertTrue(calls.any { it.contains("fun:<top>.baz(0)") })
    }
}