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

class AnnotationEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - если annotations нет, возвращает пустой список`() {
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node))

        val edges = AnnotationEdgeLinker().link(node, NodeMeta(), index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - создает ANNOTATED_WITH и DEPENDS_ON`() {
        val anno = node(id = 2L, fqn = "com.example.MyAnno", name = "MyAnno", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno))

        val edges =
            AnnotationEdgeLinker().link(
                node,
                NodeMeta(annotations = listOf("MyAnno"), imports = listOf("com.example.MyAnno")),
                index,
            )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno, EdgeKind.DEPENDS_ON),
        )
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

