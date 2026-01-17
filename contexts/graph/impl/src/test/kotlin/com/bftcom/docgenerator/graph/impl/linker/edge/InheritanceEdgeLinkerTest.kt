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

    @Test
    fun `link - null supertypes returns empty list`() {
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(child))

        val edges = InheritanceEdgeLinker().link(child, NodeMeta(supertypesResolved = null, supertypesSimple = null), index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - unresolved supertype does not create edge`() {
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(child))

        val edges = InheritanceEdgeLinker().link(
            child,
            NodeMeta(supertypesSimple = listOf("UnknownBase"), imports = emptyList()),
            index,
        )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - использует и supertypesResolved и supertypesSimple`() {
        val base1 = node(fqn = "com.base.Base1", name = "Base1", pkg = "com.base", kind = NodeKind.CLASS)
        val base2 = node(fqn = "com.base.Base2", name = "Base2", pkg = "com.base", kind = NodeKind.CLASS)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base1, base2, child))
        val meta = NodeMeta(
            imports = listOf("com.base.Base1", "com.base.Base2"),
            supertypesResolved = listOf("Base1"),
            supertypesSimple = listOf("Base2"),
        )

        val edges = InheritanceEdgeLinker().link(child, meta, index)

        assertThat(edges).hasSize(4) // 2 edges для Base1 + 2 edges для Base2
        assertThat(edges).contains(Triple(child, base1, EdgeKind.INHERITS))
        assertThat(edges).contains(Triple(child, base2, EdgeKind.INHERITS))
    }

    @Test
    fun `link - empty supertypes lists returns empty list`() {
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)
        val index = NodeIndexFactory().create(listOf(child))

        val edges = InheritanceEdgeLinker().link(
            child,
            NodeMeta(supertypesResolved = emptyList(), supertypesSimple = emptyList()),
            index,
        )

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - циклическое наследование A наследуется от B B наследуется от A`() {
        // given
        val classA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val classB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(classA, classB))

        // A наследуется от B
        val metaA = NodeMeta(
            imports = listOf("com.example.B"),
            supertypesSimple = listOf("B"),
        )

        // B наследуется от A
        val metaB = NodeMeta(
            imports = listOf("com.example.A"),
            supertypesSimple = listOf("A"),
        )

        // when
        val edgesA = InheritanceEdgeLinker().link(classA, metaA, index)
        val edgesB = InheritanceEdgeLinker().link(classB, metaB, index)

        // then
        assertThat(edgesA).contains(Triple(classA, classB, EdgeKind.INHERITS))
        assertThat(edgesB).contains(Triple(classB, classA, EdgeKind.INHERITS))
    }

    @Test
    fun `link - цепочка наследования с циклом A наследуется от B B наследуется от C C наследуется от A`() {
        // given
        val classA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val classB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)
        val classC = node(fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(classA, classB, classC))

        // A наследуется от B
        val metaA = NodeMeta(
            imports = listOf("com.example.B"),
            supertypesSimple = listOf("B"),
        )

        // B наследуется от C
        val metaB = NodeMeta(
            imports = listOf("com.example.C"),
            supertypesSimple = listOf("C"),
        )

        // C наследуется от A
        val metaC = NodeMeta(
            imports = listOf("com.example.A"),
            supertypesSimple = listOf("A"),
        )

        // when
        val edgesA = InheritanceEdgeLinker().link(classA, metaA, index)
        val edgesB = InheritanceEdgeLinker().link(classB, metaB, index)
        val edgesC = InheritanceEdgeLinker().link(classC, metaC, index)

        // then
        assertThat(edgesA).contains(Triple(classA, classB, EdgeKind.INHERITS))
        assertThat(edgesB).contains(Triple(classB, classC, EdgeKind.INHERITS))
        assertThat(edgesC).contains(Triple(classC, classA, EdgeKind.INHERITS))
    }

    @Test
    fun `link - множественные супертипы с циклической зависимостью`() {
        // given
        val base = node(fqn = "com.example.Base", name = "Base", pkg = "com.example", kind = NodeKind.CLASS)
        val child1 = node(fqn = "com.example.Child1", name = "Child1", pkg = "com.example", kind = NodeKind.CLASS)
        val child2 = node(fqn = "com.example.Child2", name = "Child2", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base, child1, child2))

        // Child1 наследуется от Base
        val meta1 = NodeMeta(
            imports = listOf("com.example.Base"),
            supertypesSimple = listOf("Base"),
        )

        // Child2 наследуется от Base и Child1 (множественное наследование через супертипы)
        val meta2 = NodeMeta(
            imports = listOf("com.example.Base", "com.example.Child1"),
            supertypesSimple = listOf("Base", "Child1"),
        )

        // Base наследуется от Child2 (цикл!)
        val metaBase = NodeMeta(
            imports = listOf("com.example.Child2"),
            supertypesSimple = listOf("Child2"),
        )

        // when
        val edges1 = InheritanceEdgeLinker().link(child1, meta1, index)
        val edges2 = InheritanceEdgeLinker().link(child2, meta2, index)
        val edgesBase = InheritanceEdgeLinker().link(base, metaBase, index)

        // then
        assertThat(edges1).contains(Triple(child1, base, EdgeKind.INHERITS))
        assertThat(edges2).contains(Triple(child2, base, EdgeKind.INHERITS))
        assertThat(edges2).contains(Triple(child2, child1, EdgeKind.INHERITS))
        assertThat(edgesBase).contains(Triple(base, child2, EdgeKind.INHERITS))
        // Цикл: base -> child2 -> base
    }

    @Test
    fun `link - ENUM наследуется от другого класса создает INHERITS`() {
        val base = node(fqn = "com.base.Base", name = "Base", pkg = "com.base", kind = NodeKind.CLASS)
        val enum = node(fqn = "com.example.MyEnum", name = "MyEnum", pkg = "com.example", kind = NodeKind.ENUM)

        val index = NodeIndexFactory().create(listOf(base, enum))
        val meta = NodeMeta(
            imports = listOf("com.base.Base"),
            supertypesSimple = listOf("Base"),
        )

        val edges = InheritanceEdgeLinker().link(enum, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(enum, base, EdgeKind.INHERITS),
            Triple(enum, base, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - RECORD наследуется от другого класса создает INHERITS`() {
        val base = node(fqn = "com.base.Base", name = "Base", pkg = "com.base", kind = NodeKind.CLASS)
        val record = node(fqn = "com.example.MyRecord", name = "MyRecord", pkg = "com.example", kind = NodeKind.RECORD)

        val index = NodeIndexFactory().create(listOf(base, record))
        val meta = NodeMeta(
            imports = listOf("com.base.Base"),
            supertypesSimple = listOf("Base"),
        )

        val edges = InheritanceEdgeLinker().link(record, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(record, base, EdgeKind.INHERITS),
            Triple(record, base, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - SERVICE наследуется от интерфейса создает IMPLEMENTS`() {
        val iface = node(fqn = "com.other.I", name = "I", pkg = "com.other", kind = NodeKind.INTERFACE)
        val service = node(fqn = "com.example.MyService", name = "MyService", pkg = "com.example", kind = NodeKind.SERVICE)

        val index = NodeIndexFactory().create(listOf(iface, service))
        val meta = NodeMeta(
            imports = listOf("com.other.I"),
            supertypesSimple = listOf("I"),
        )

        val edges = InheritanceEdgeLinker().link(service, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(service, iface, EdgeKind.IMPLEMENTS),
            Triple(service, iface, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - empty imports использует пакет для разрешения`() {
        val base = node(fqn = "com.example.Base", name = "Base", pkg = "com.example", kind = NodeKind.CLASS)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base, child))
        val meta = NodeMeta(
            imports = emptyList(),
            supertypesSimple = listOf("Base"),
        )

        val edges = InheritanceEdgeLinker().link(child, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(child, base, EdgeKind.INHERITS),
            Triple(child, base, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - null imports использует пустой список`() {
        val base = node(fqn = "com.example.Base", name = "Base", pkg = "com.example", kind = NodeKind.CLASS)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base, child))
        val meta = NodeMeta(
            imports = null,
            supertypesSimple = listOf("Base"),
        )

        val edges = InheritanceEdgeLinker().link(child, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(child, base, EdgeKind.INHERITS),
            Triple(child, base, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - null packageName использует пустую строку`() {
        val base = node(fqn = "com.example.Base", name = "Base", pkg = "com.example", kind = NodeKind.CLASS)
        val child = node(fqn = "com.example.Child", name = "Child", pkg = null, kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(base, child))
        val meta = NodeMeta(
            imports = listOf("com.example.Base"),
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

