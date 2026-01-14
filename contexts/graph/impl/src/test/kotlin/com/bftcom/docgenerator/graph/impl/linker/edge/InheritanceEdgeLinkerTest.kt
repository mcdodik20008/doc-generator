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

class InheritanceEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - interface дает IMPLEMENTS и DEPENDS_ON`() {
        val iface = node(fqn = "com.other.I", name = "I", pkg = "com.other", kind = NodeKind.INTERFACE)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(iface, child))
        val meta =
            NodeMeta(
                imports = listOf("com.other.I"),
                supertypesSimple = listOf("I"),
            )

        val edges = InheritanceEdgeLinker().link(child, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(child, iface, EdgeKind.IMPLEMENTS),
            Triple(child, iface, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - class дает INHERITS и DEPENDS_ON`() {
        val base = node(fqn = "com.base.Base", name = "Base", pkg = "com.base", kind = NodeKind.CLASS)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base, child))
        val meta =
            NodeMeta(
                imports = listOf("com.base.Base"),
                supertypesSimple = listOf("Base"),
            )

        val edges = InheritanceEdgeLinker().link(child, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(child, base, EdgeKind.INHERITS),
            Triple(child, base, EdgeKind.DEPENDS_ON),
        )
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

