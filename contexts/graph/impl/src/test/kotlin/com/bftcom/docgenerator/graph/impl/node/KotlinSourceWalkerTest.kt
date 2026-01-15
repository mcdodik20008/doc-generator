package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.node.KDocFetcher
import com.bftcom.docgenerator.graph.api.node.SourceVisitor
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class KotlinSourceWalkerTest {
    @Test
    fun `walk - требует директорию`() {
        val kdoc = mockk<KDocFetcher>(relaxed = true)
        val walker = KotlinSourceWalker(kdoc)

        val file = Files.createTempFile("not-dir", ".kt")
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            walker.walk(file, visitor = NoopVisitor(), classpath = emptyList())
        }
        assertThat(ex.message).contains("Source root must be a directory")
    }

    @Test
    fun `walk - вызывает visitor для файла типов полей и функций`(@TempDir root: Path) {
        val kdoc = mockk<KDocFetcher>(relaxed = true).also {
            every { it.parseKDoc(any()) } returns null
            every { it.toDocString(any()) } returns ""
            every { it.toMeta(any()) } returns null
        }
        val walker = KotlinSourceWalker(kdoc)

        val src =
            """
            package com.example
            import kotlin.collections.List

            interface Api {
              fun abstractFun(x: Int): String
            }

            class Foo {
              val x: Int = 1

              fun block(): Int {
                println("x")
                bar()
                baz.qux()
                throw IllegalStateException("boom")
              }

              fun expr() = bar()
            }

            fun topLevel(a: Int) = a + 1
            """.trimIndent()

        Files.writeString(root.resolve("Foo.kt"), src)

        val events = CollectingVisitor()
        walker.walk(root, visitor = events, classpath = emptyList<File>())

        // Проверяем, что хотя бы базовые сущности распознаны
        assertThat(events.files).hasSize(1)
        assertThat(events.files[0].pkgFqn).isEqualTo("com.example")
        assertThat(events.types.map { it.simpleName }).contains("Api", "Foo")
        assertThat(events.fields.map { it.name }).contains("x")
        assertThat(events.functions.map { it.name }).contains("abstractFun", "block", "expr", "topLevel")
    }

    private class NoopVisitor : SourceVisitor

    private class CollectingVisitor : SourceVisitor {
        val all = mutableListOf<RawDecl>()
        val files = mutableListOf<RawFileUnit>()
        val types = mutableListOf<RawType>()
        val fields = mutableListOf<RawField>()
        val functions = mutableListOf<RawFunction>()

        override fun onDecl(raw: RawDecl) {
            all += raw
            super.onDecl(raw)
        }

        override fun onFile(unit: RawFileUnit) {
            files += unit
        }

        override fun onType(decl: RawType) {
            types += decl
        }

        override fun onField(decl: RawField) {
            fields += decl
        }

        override fun onFunction(decl: RawFunction) {
            functions += decl
        }
    }
}

