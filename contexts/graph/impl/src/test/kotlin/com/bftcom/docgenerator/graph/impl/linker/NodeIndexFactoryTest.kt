package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NodeIndexFactoryTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `resolveType - приоритет FQN`() {
        val bar = node(fqn = "com.other.Bar", name = "Bar", pkg = "com.other", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(bar))

        val resolved = index.resolveType("com.other.Bar", imports = emptyList(), pkg = "com.example")

        assertThat(resolved?.fqn).isEqualTo("com.other.Bar")
    }

    @Test
    fun `resolveType - импорт имеет приоритет над пакетом`() {
        val bar = node(fqn = "com.other.Bar", name = "Bar", pkg = "com.other", kind = NodeKind.CLASS)
        val barLocal = node(fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(bar, barLocal))

        val resolved = index.resolveType("Bar", imports = listOf("com.other.Bar"), pkg = "com.example")

        assertThat(resolved?.fqn).isEqualTo("com.other.Bar")
    }

    @Test
    fun `resolveType - берёт тип из текущего пакета если нет импорта`() {
        val foo = node(fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(foo))

        val resolved = index.resolveType("Foo", imports = emptyList(), pkg = "com.example")

        assertThat(resolved?.fqn).isEqualTo("com.example.Foo")
    }

    @Test
    fun `resolveType - корректно обрабатывает nullable и generics в FQN`() {
        val foo = node(fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(foo))

        val resolvedNullable = index.resolveType("Foo?", imports = emptyList(), pkg = "com.example")
        val resolvedGeneric = index.resolveType("com.example.Foo<Bar>", imports = emptyList(), pkg = "com.example")

        assertThat(resolvedNullable?.fqn).isEqualTo("com.example.Foo")
        assertThat(resolvedGeneric?.fqn).isEqualTo("com.example.Foo")
    }

    private fun node(
        fqn: String,
        name: String,
        pkg: String,
        kind: NodeKind,
    ): Node =
        Node(
            id = null,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
        )
}

