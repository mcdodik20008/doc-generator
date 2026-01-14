package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.impl.linker.NodeIndexFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SignatureDependencyLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - берет типы из meta paramTypes и returnType и использует owner как source`() {
        val owner = node(id = 10L, fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                id = 11L,
                fqn = "com.example.Service.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo): Bar",
            )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(owner, fn, foo, bar))
        val meta =
            NodeMeta(
                ownerFqn = owner.fqn,
                imports = listOf("com.example.Foo", "com.example.Bar"),
                paramTypes = listOf("Foo"),
                returnType = "Bar",
            )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(owner, foo, EdgeKind.DEPENDS_ON),
            Triple(owner, bar, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - парсит типы из signature если meta пустая`() {
        val fn =
            node(
                id = 11L,
                fqn = "com.example.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo, y: Bar): Baz",
            )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val baz = node(id = 22L, fqn = "com.example.Baz", name = "Baz", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo, bar, baz))
        val meta = NodeMeta(imports = listOf("com.example.Foo", "com.example.Bar", "com.example.Baz"))

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, foo, EdgeKind.DEPENDS_ON),
            Triple(fn, bar, EdgeKind.DEPENDS_ON),
            Triple(fn, baz, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - не создает self-edge когда тип совпадает с source`() {
        val owner = node(id = 10L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                id = 11L,
                fqn = "com.example.Foo.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo): Foo",
            )
        val index = NodeIndexFactory().create(listOf(owner, fn))
        val meta =
            NodeMeta(
                ownerFqn = owner.fqn,
                imports = listOf("com.example.Foo"),
                paramTypes = listOf("Foo"),
                returnType = "Foo",
            )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).isEmpty()
    }

    private fun node(
        id: Long,
        fqn: String,
        name: String,
        pkg: String,
        kind: NodeKind,
        signature: String? = null,
    ): Node =
        Node(
            id = id,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
            signature = signature,
        )
}

