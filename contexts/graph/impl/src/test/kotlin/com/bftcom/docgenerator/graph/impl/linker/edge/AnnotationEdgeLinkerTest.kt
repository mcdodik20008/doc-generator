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

    @Test
    fun `link - null annotations returns empty list`() {
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node))

        val edges = AnnotationEdgeLinker().link(node, NodeMeta(annotations = null), index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - unresolved annotation does not create edge`() {
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("UnknownAnno"), imports = emptyList()),
            index,
        )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - empty imports still resolves from same package`() {
        val anno = node(id = 2L, fqn = "com.example.MyAnno", name = "MyAnno", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("MyAnno"), imports = emptyList()),
            index,
        )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - множественные аннотации создают все edges`() {
        val anno1 = node(id = 2L, fqn = "com.example.MyAnno1", name = "MyAnno1", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val anno2 = node(id = 3L, fqn = "com.example.MyAnno2", name = "MyAnno2", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno1, anno2))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("MyAnno1", "MyAnno2"), imports = listOf("com.example.MyAnno1", "com.example.MyAnno2")),
            index,
        )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno1, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno1, EdgeKind.DEPENDS_ON),
            Triple(node, anno2, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno2, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - частично разрешенные аннотации создают edges только для найденных`() {
        val anno1 = node(id = 2L, fqn = "com.example.MyAnno1", name = "MyAnno1", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno1))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("MyAnno1", "UnknownAnno"), imports = listOf("com.example.MyAnno1")),
            index,
        )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno1, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno1, EdgeKind.DEPENDS_ON),
        )
        assertThat(edges).hasSize(2)
    }

    @Test
    fun `link - null imports использует пустой список`() {
        val anno = node(id = 2L, fqn = "com.example.MyAnno", name = "MyAnno", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("MyAnno"), imports = null),
            index,
        )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - null packageName использует пустую строку`() {
        val anno = node(id = 2L, fqn = "com.example.MyAnno", name = "MyAnno", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = null, kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node, anno))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = listOf("MyAnno"), imports = listOf("com.example.MyAnno")),
            index,
        )

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(node, anno, EdgeKind.ANNOTATED_WITH),
            Triple(node, anno, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - циклические зависимости через аннотации A аннотирован B B аннотирован A`() {
        val annoA = node(id = 2L, fqn = "com.example.AnnoA", name = "AnnoA", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val annoB = node(id = 3L, fqn = "com.example.AnnoB", name = "AnnoB", pkg = "com.example", kind = NodeKind.ANNOTATION)
        val nodeA = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val nodeB = node(id = 4L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(nodeA, nodeB, annoA, annoB))

        // A аннотирован AnnoA, который в свою очередь может быть аннотирован AnnoB
        val edgesA = AnnotationEdgeLinker().link(
            nodeA,
            NodeMeta(annotations = listOf("AnnoA"), imports = listOf("com.example.AnnoA")),
            index,
        )

        val edgesB = AnnotationEdgeLinker().link(
            nodeB,
            NodeMeta(annotations = listOf("AnnoB"), imports = listOf("com.example.AnnoB")),
            index,
        )

        assertThat(edgesA).contains(Triple(nodeA, annoA, EdgeKind.ANNOTATED_WITH))
        assertThat(edgesB).contains(Triple(nodeB, annoB, EdgeKind.ANNOTATED_WITH))
    }

    @Test
    fun `link - empty annotations list returns empty list`() {
        val node = node(id = 1L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(node))

        val edges = AnnotationEdgeLinker().link(
            node,
            NodeMeta(annotations = emptyList()),
            index,
        )

        assertThat(edges).isEmpty()
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

