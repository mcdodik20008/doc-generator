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

    @Test
    fun `walk - не обходит build out node_modules git и dependencies`(@TempDir root: Path) {
        val kdoc = mockk<KDocFetcher>(relaxed = true)
        val walker = KotlinSourceWalker(kdoc)

        Files.writeString(root.resolve("A.kt"), "package p\nfun f() = 1\n")
        Files.createDirectories(root.resolve("build")).also {
            Files.writeString(it.resolve("B.kt"), "package p\nfun g() = 2\n")
        }
        Files.createDirectories(root.resolve("out")).also {
            Files.writeString(it.resolve("C.kt"), "package p\nfun h() = 3\n")
        }
        Files.createDirectories(root.resolve("node_modules")).also {
            Files.writeString(it.resolve("D.kt"), "package p\nfun i() = 4\n")
        }
        Files.createDirectories(root.resolve(".git")).also {
            Files.writeString(it.resolve("E.kt"), "package p\nfun j() = 5\n")
        }
        Files.writeString(root.resolve("dependencies.kt"), "package p\nfun k() = 6\n")

        val events = CollectingVisitor()
        walker.walk(root, visitor = events, classpath = emptyList<File>())

        assertThat(events.files).hasSize(1)
        assertThat(events.files[0].filePath).endsWith("A.kt")
    }

    @Test
    fun `walk - для абстрактной функции использует текстовый парсер`(@TempDir root: Path) {
        val kdoc = mockk<KDocFetcher>(relaxed = true)
        val walker = KotlinSourceWalker(kdoc)

        val src =
            """
            package com.example
            interface Api {
              fun abstractFun(x: Int): String
            }
            """.trimIndent()
        Files.writeString(root.resolve("Api.kt"), src)

        val events = CollectingVisitor()
        walker.walk(root, visitor = events, classpath = emptyList<File>())

        val abstractFun =
            events.functions.first { it.name == "abstractFun" }
        assertThat(abstractFun.rawUsages)
            .contains(com.bftcom.docgenerator.domain.node.RawUsage.Simple("abstractFun", isCall = true))
    }

    @Test
    fun `walk - определяет kindRepr и throws через PSI`(@TempDir root: Path) {
        val kdoc = mockk<KDocFetcher>(relaxed = true)
        val walker = KotlinSourceWalker(kdoc)

        val src =
            """
            package com.example

            object Singleton

            enum class Color { RED }

            data class Person(val name: String)

            object foo {
              object bar {
                class Baz : RuntimeException()
              }
            }

            class MyException : RuntimeException()

            class Thrower {
              fun boom() {
                throw MyException()
                throw foo.bar.Baz()
              }
            }
            """.trimIndent()

        Files.writeString(root.resolve("KindsAndThrows.kt"), src)

        val events = CollectingVisitor()
        walker.walk(root, visitor = events, classpath = emptyList<File>())

        val typesByName = events.types.associateBy { it.simpleName }
        assertThat(typesByName["Singleton"]?.kindRepr).isEqualTo("object")
        assertThat(typesByName["Color"]?.kindRepr).isEqualTo("enum")
        assertThat(typesByName["Person"]?.kindRepr).isEqualTo("record")

        val boom = events.functions.first { it.name == "boom" }
        assertThat(boom.throwsRepr).contains("MyException", "foo.bar.Baz")
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

