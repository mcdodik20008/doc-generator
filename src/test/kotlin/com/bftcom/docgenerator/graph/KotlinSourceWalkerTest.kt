package com.bftcom.docgenerator.graph

import org.junit.jupiter.api.Assertions.assertTrue
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.yourpkg.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.impl.KDocFetcherImpl
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import com.bftcom.docgenerator.graph.impl.KotlinSourceWalker
import com.yourpkg.graph.api.model.RawUsage

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

        val walker = KotlinSourceWalker(KDocFetcherImpl())
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
                usages: List<RawUsage>
            ) {
                TODO("Not yet implemented")
            }

            override fun onFileContext(
                pkgFqn: String,
                filePath: String,
                imports: List<String>
            ) {
                TODO("Not yet implemented")
            }

            override fun onTypeEx(
                kind: NodeKind,
                fqn: String,
                pkgFqn: String,
                name: String,
                filePath: String,
                spanLines: IntRange,
                supertypesSimple: List<String>,
                sourceCode: String?,
                signature: String?,
                docComment: String?,
                kdocMeta: Map<String, Any?>?
            ) {
                TODO("Not yet implemented")
            }

            override fun onFieldEx(
                ownerFqn: String,
                name: String,
                filePath: String,
                spanLines: IntRange,
                sourceCode: String?,
                docComment: String?,
                kdocMeta: Map<String, Any?>?
            ) {
                TODO("Not yet implemented")
            }

            override fun onFunctionEx(
                ownerFqn: String?,
                name: String,
                paramNames: List<String>,
                filePath: String,
                spanLines: IntRange,
                usages: List<RawUsage>,
                sourceCode: String?,
                signature: String?,
                docComment: String?,
                annotations: Set<String>?,
                kdocMeta: Map<String, Any?>?
            ) {
                TODO("Not yet implemented")
            }
        }

        walker.walk(src, v, emptyList())

        // Assertions (упрощённо, важные факты)
        assertTrue(calls.any { it.startsWith("pkg:foo") })
        assertTrue(calls.any { it.startsWith("type:CLASS:foo.A") })
        assertTrue(calls.any { it.contains("Runnable") })
        assertTrue(calls.any { it == "field:foo.A.x" })
        assertTrue(calls.any { it.contains("fun:foo.A.bar(1)") && it.contains("println") && it.contains("baz") })
        assertTrue(calls.any { it.contains("fun:<top>.baz(0)") })
    }
}