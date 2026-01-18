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

    @Test
    fun `linkContains - создает CONTAINS для всех TYPE_KINDS`() {
        val pkg = node(fqn = "com.example", name = "com.example", pkg = "com.example", kind = NodeKind.PACKAGE)
        val classes = listOf(
            node(fqn = "com.example.Interface", name = "Interface", pkg = "com.example", kind = NodeKind.INTERFACE),
            node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.SERVICE),
            node(fqn = "com.example.Record", name = "Record", pkg = "com.example", kind = NodeKind.RECORD),
            node(fqn = "com.example.Mapper", name = "Mapper", pkg = "com.example", kind = NodeKind.MAPPER),
            node(fqn = "com.example.Endpoint", name = "Endpoint", pkg = "com.example", kind = NodeKind.ENDPOINT),
            node(fqn = "com.example.Class", name = "Class", pkg = "com.example", kind = NodeKind.CLASS),
            node(fqn = "com.example.Enum", name = "Enum", pkg = "com.example", kind = NodeKind.ENUM),
            node(fqn = "com.example.Config", name = "Config", pkg = "com.example", kind = NodeKind.CONFIG),
        )

        val all = listOf(pkg) + classes
        val index = NodeIndexFactory().create(all)

        val edges = StructuralEdgeLinker().linkContains(all, index) { NodeMeta() }

        classes.forEach { cls ->
            assertThat(edges).contains(Triple(pkg, cls, EdgeKind.CONTAINS))
        }
    }

    @Test
    fun `linkContains - создает CONTAINS для всех MEMBER_KINDS`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val members = listOf(
            node(fqn = "com.example.Service.method", name = "method", pkg = "com.example", kind = NodeKind.METHOD),
            node(fqn = "com.example.Service.field", name = "field", pkg = "com.example", kind = NodeKind.FIELD),
            node(fqn = "com.example.Service.endpoint", name = "endpoint", pkg = "com.example", kind = NodeKind.ENDPOINT),
            node(fqn = "com.example.Service.job", name = "job", pkg = "com.example", kind = NodeKind.JOB),
            node(fqn = "com.example.Service.topic", name = "topic", pkg = "com.example", kind = NodeKind.TOPIC),
        )

        val all = listOf(type) + members
        val index = NodeIndexFactory().create(all)

        val metaOf: (Node) -> NodeMeta = { n ->
            if (n.kind in setOf(NodeKind.METHOD, NodeKind.FIELD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)) {
                NodeMeta(ownerFqn = type.fqn)
            } else {
                NodeMeta()
            }
        }

        val edges = StructuralEdgeLinker().linkContains(all, index, metaOf)

        members.forEach { member ->
            assertThat(edges).contains(Triple(type, member, EdgeKind.CONTAINS))
        }
    }

    @Test
    fun `linkContains - не создает CONTAINS для типа без packageName`() {
        val pkg = node(fqn = "com.example", name = "com.example", pkg = "com.example", kind = NodeKind.PACKAGE)
        val type = node(fqn = "com.example.Service", name = "Service", pkg = null, kind = NodeKind.CLASS)

        val all = listOf(pkg, type)
        val index = NodeIndexFactory().create(all)

        val edges = StructuralEdgeLinker().linkContains(all, index) { NodeMeta() }

        assertThat(edges).doesNotContain(Triple(pkg, type, EdgeKind.CONTAINS))
    }

    @Test
    fun `linkContains - не создает CONTAINS для типа с packageName которого нет в индексе`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.other", kind = NodeKind.CLASS)

        val all = listOf(type)
        val index = NodeIndexFactory().create(all)

        val edges = StructuralEdgeLinker().linkContains(all, index) { NodeMeta() }

        assertThat(edges).isEmpty()
    }

    @Test
    fun `linkContains - не создает CONTAINS для member с ownerFqn которого нет в индексе`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val member = node(fqn = "com.example.Service.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)

        val all = listOf(member) // type не в списке
        val index = NodeIndexFactory().create(all)

        val metaOf: (Node) -> NodeMeta = { n ->
            if (n.fqn == member.fqn) NodeMeta(ownerFqn = type.fqn) else NodeMeta()
        }

        val edges = StructuralEdgeLinker().linkContains(all, index, metaOf)

        assertThat(edges).doesNotContain(Triple(type, member, EdgeKind.CONTAINS))
    }

    @Test
    fun `linkContains - не создает CONTAINS для member без ownerFqn в meta`() {
        val type = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val member = node(fqn = "com.example.Service.doIt", name = "doIt", pkg = "com.example", kind = NodeKind.METHOD)

        val all = listOf(type, member)
        val index = NodeIndexFactory().create(all)

        val edges = StructuralEdgeLinker().linkContains(all, index) { NodeMeta(ownerFqn = null) }

        assertThat(edges).doesNotContain(Triple(type, member, EdgeKind.CONTAINS))
    }

    private fun node(
        fqn: String,
        name: String,
        pkg: String?,
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

