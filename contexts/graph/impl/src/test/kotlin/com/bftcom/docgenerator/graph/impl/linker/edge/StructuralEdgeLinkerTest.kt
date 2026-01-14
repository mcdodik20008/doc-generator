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

class StructuralEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `linkContains - создает CONTAINS между пакетом и типом, и между типом и членом`() {
        val pkg = node(fqn = "com.example", name = "com.example", pkg = "com.example", kind = NodeKind.PACKAGE)
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val member = node(fqn = "com.example.Service.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)

        val all = listOf(pkg, type, member)
        val index = NodeIndexFactory().create(all)

        val metaOf: (Node) -> NodeMeta = { n ->
            if (n.fqn == member.fqn) NodeMeta(ownerFqn = type.fqn) else NodeMeta()
        }

        val edges = StructuralEdgeLinker().linkContains(all, index, metaOf)

        assertThat(edges).contains(
            Triple(pkg, type, EdgeKind.CONTAINS),
            Triple(type, member, EdgeKind.CONTAINS),
        )
    }

    @Test
    fun `linkContains - не создает CONTAINS для member без ownerFqn`() {
        val pkg = node(fqn = "com.example", name = "com.example", pkg = "com.example", kind = NodeKind.PACKAGE)
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val member = node(fqn = "com.example.Service.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)

        val all = listOf(pkg, type, member)
        val index = NodeIndexFactory().create(all)

        val edges = StructuralEdgeLinker().linkContains(all, index) { NodeMeta() }

        assertThat(edges).doesNotContain(Triple(type, member, EdgeKind.CONTAINS))
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

