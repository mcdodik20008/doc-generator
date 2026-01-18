package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.impl.linker.NodeIndexFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThrowEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - если throwsTypes нет, возвращает пустой список`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))

        val edges = ThrowEdgeLinker().link(fn, NodeMeta(), index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - создает THROWS`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val ex = node(id = 2L, fqn = "com.example.MyEx", name = "MyEx", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx"), imports = listOf("com.example.MyEx")),
                index,
            )

        assertThat(edges).containsExactly(Triple(fn, ex, EdgeKind.THROWS))
    }

    @Test
    fun `link - множественные исключения создают все THROWS edges`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val ex1 = node(id = 2L, fqn = "com.example.MyEx1", name = "MyEx1", pkg = "com.example", kind = NodeKind.CLASS)
        val ex2 = node(id = 3L, fqn = "com.example.MyEx2", name = "MyEx2", pkg = "com.example", kind = NodeKind.CLASS)
        val ex3 = node(id = 4L, fqn = "com.example.MyEx3", name = "MyEx3", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex1, ex2, ex3))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx1", "MyEx2", "MyEx3"), imports = listOf("com.example.MyEx1", "com.example.MyEx2", "com.example.MyEx3")),
                index,
            )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, ex1, EdgeKind.THROWS),
            Triple(fn, ex2, EdgeKind.THROWS),
            Triple(fn, ex3, EdgeKind.THROWS),
        )
    }

    @Test
    fun `link - unresolved exception type не создает edge`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("UnknownEx"), imports = emptyList()),
                index,
            )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - частично разрешенные исключения создают edges только для найденных`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val ex1 = node(id = 2L, fqn = "com.example.MyEx1", name = "MyEx1", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex1))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx1", "UnknownEx"), imports = listOf("com.example.MyEx1")),
                index,
            )

        assertThat(edges).containsExactly(Triple(fn, ex1, EdgeKind.THROWS))
        assertThat(edges).hasSize(1)
    }

    @Test
    fun `link - empty throwsTypes returns empty list`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))

        val edges = ThrowEdgeLinker().link(
            fn,
            NodeMeta(throwsTypes = emptyList()),
            index,
        )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - null throwsTypes returns empty list`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val index = NodeIndexFactory().create(listOf(fn))

        val edges = ThrowEdgeLinker().link(
            fn,
            NodeMeta(throwsTypes = null),
            index,
        )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - empty imports использует пакет для разрешения`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val ex = node(id = 2L, fqn = "com.example.MyEx", name = "MyEx", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx"), imports = emptyList()),
                index,
            )

        assertThat(edges).containsExactly(Triple(fn, ex, EdgeKind.THROWS))
    }

    @Test
    fun `link - null imports использует пустой список`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)
        val ex = node(id = 2L, fqn = "com.example.MyEx", name = "MyEx", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx"), imports = null),
                index,
            )

        assertThat(edges).containsExactly(Triple(fn, ex, EdgeKind.THROWS))
    }

    @Test
    fun `link - null packageName использует пустую строку`() {
        val fn = node(id = 1L, fqn = "com.example.doIt", name = "doIt", pkg = null, kind = NodeKind.METHOD)
        val ex = node(id = 2L, fqn = "com.example.MyEx", name = "MyEx", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fn, ex))

        val edges =
            ThrowEdgeLinker().link(
                fn,
                NodeMeta(throwsTypes = listOf("MyEx"), imports = listOf("com.example.MyEx")),
                index,
            )

        assertThat(edges).containsExactly(Triple(fn, ex, EdgeKind.THROWS))
    }

    @Test
    fun `link - циклические зависимости через исключения A бросает B B бросает A`() {
        val fnA = node(id = 1L, fqn = "com.example.methodA", name = "methodA", pkg = "com.example", kind = NodeKind.METHOD)
        val fnB = node(id = 2L, fqn = "com.example.methodB", name = "methodB", pkg = "com.example", kind = NodeKind.METHOD)
        val exA = node(id = 3L, fqn = "com.example.ExA", name = "ExA", pkg = "com.example", kind = NodeKind.CLASS)
        val exB = node(id = 4L, fqn = "com.example.ExB", name = "ExB", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(fnA, fnB, exA, exB))

        // A бросает ExB
        val edgesA =
            ThrowEdgeLinker().link(
                fnA,
                NodeMeta(throwsTypes = listOf("ExB"), imports = listOf("com.example.ExB")),
                index,
            )

        // B бросает ExA
        val edgesB =
            ThrowEdgeLinker().link(
                fnB,
                NodeMeta(throwsTypes = listOf("ExA"), imports = listOf("com.example.ExA")),
                index,
            )

        assertThat(edgesA).contains(Triple(fnA, exB, EdgeKind.THROWS))
        assertThat(edgesB).contains(Triple(fnB, exA, EdgeKind.THROWS))
    }

    private fun node(
        id: Long,
        fqn: String,
        name: String,
        pkg: String?,
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

