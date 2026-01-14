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

