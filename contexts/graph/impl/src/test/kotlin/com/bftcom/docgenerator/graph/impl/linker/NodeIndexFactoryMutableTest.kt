package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NodeIndexFactoryMutableTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `addNode - обновляет findByFqn для существующего fqn`() {
        val initial =
            node(
                id = 1L,
                fqn = "com.example.Foo",
                name = "Foo",
                pkg = "com.example",
                kind = NodeKind.CLASS,
            )
        val updated =
            node(
                id = 1L,
                fqn = "com.example.Foo",
                name = "Foo",
                pkg = "com.example",
                kind = NodeKind.SERVICE, // другое значение
            )

        val index = NodeIndexFactory().createMutable(listOf(initial))
        assertThat(index.findByFqn("com.example.Foo")?.kind).isEqualTo(NodeKind.CLASS)

        index.addNode(updated)

        assertThat(index.findByFqn("com.example.Foo")).isSameAs(updated)
        assertThat(index.findByFqn("com.example.Foo")?.kind).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `addNodes - добавляет новые узлы`() {
        val index = NodeIndexFactory().createMutable(emptyList())
        val a = node(id = 1L, fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val b = node(id = 2L, fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.INTERFACE)

        index.addNodes(listOf(a, b))

        assertThat(index.findByFqn("com.example.A")).isSameAs(a)
        assertThat(index.findByFqn("com.example.B")).isSameAs(b)
    }

    @Test
    fun `resolveType - после addNode начинает разрешать новый тип`() {
        val index = NodeIndexFactory().createMutable(emptyList())
        val foo = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        assertThat(index.resolveType("Foo", imports = listOf("com.example.Foo"), pkg = "com.other")).isNull()

        index.addNode(foo)

        val resolved = index.resolveType("Foo", imports = listOf("com.example.Foo"), pkg = "com.other")
        assertThat(resolved).isSameAs(foo)
    }

    private fun node(
        id: Long,
        fqn: String,
        name: String,
        pkg: String,
        kind: NodeKind,
    ): Node =
        Node(
            id = id,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
        )
}

