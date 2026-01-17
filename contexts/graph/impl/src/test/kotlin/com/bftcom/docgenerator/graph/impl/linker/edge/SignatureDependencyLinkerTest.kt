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

class SignatureDependencyLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - берет типы из meta paramTypes и returnType и использует owner как source`() {
        val owner = node(id = 10L, fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                id = 11L,
                fqn = "com.example.Service.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo): Bar",
            )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(owner, fn, foo, bar))
        val meta =
            NodeMeta(
                ownerFqn = owner.fqn,
                imports = listOf("com.example.Foo", "com.example.Bar"),
                paramTypes = listOf("Foo"),
                returnType = "Bar",
            )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(owner, foo, EdgeKind.DEPENDS_ON),
            Triple(owner, bar, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - парсит типы из signature если meta пустая`() {
        val fn =
            node(
                id = 11L,
                fqn = "com.example.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo, y: Bar): Baz",
            )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val baz = node(id = 22L, fqn = "com.example.Baz", name = "Baz", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo, bar, baz))
        val meta = NodeMeta(imports = listOf("com.example.Foo", "com.example.Bar", "com.example.Baz"))

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, foo, EdgeKind.DEPENDS_ON),
            Triple(fn, bar, EdgeKind.DEPENDS_ON),
            Triple(fn, baz, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - не создает self-edge когда тип совпадает с source`() {
        val owner = node(id = 10L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val fn =
            node(
                id = 11L,
                fqn = "com.example.Foo.doIt",
                name = "doIt",
                pkg = "com.example",
                kind = NodeKind.METHOD,
                signature = "fun doIt(x: Foo): Foo",
            )
        val index = NodeIndexFactory().create(listOf(owner, fn))
        val meta =
            NodeMeta(
                ownerFqn = owner.fqn,
                imports = listOf("com.example.Foo"),
                paramTypes = listOf("Foo"),
                returnType = "Foo",
            )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - циклическая зависимость A зависит от B B зависит от A`() {
        // given
        val classA = node(id = 10L, fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val methodA = node(id = 11L, fqn = "com.example.A.methodA", name = "methodA", pkg = "com.example", kind = NodeKind.METHOD)

        val classB = node(id = 20L, fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)
        val methodB = node(id = 21L, fqn = "com.example.B.methodB", name = "methodB", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(classA, methodA, classB, methodB))

        // methodA зависит от B (параметр типа B)
        val metaA = NodeMeta(
            ownerFqn = classA.fqn,
            imports = listOf("com.example.B"),
            paramTypes = listOf("B"),
        )

        // methodB зависит от A (параметр типа A)
        val metaB = NodeMeta(
            ownerFqn = classB.fqn,
            imports = listOf("com.example.A"),
            paramTypes = listOf("A"),
        )

        // when
        val edgesA = SignatureDependencyLinker().link(methodA, metaA, index)
        val edgesB = SignatureDependencyLinker().link(methodB, metaB, index)

        // then
        assertThat(edgesA).contains(Triple(classA, classB, EdgeKind.DEPENDS_ON))
        assertThat(edgesB).contains(Triple(classB, classA, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - цепочка зависимостей с циклом A зависит от B B зависит от C C зависит от A`() {
        // given
        val classA = node(id = 10L, fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val methodA = node(id = 11L, fqn = "com.example.A.methodA", name = "methodA", pkg = "com.example", kind = NodeKind.METHOD)

        val classB = node(id = 20L, fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)
        val methodB = node(id = 21L, fqn = "com.example.B.methodB", name = "methodB", pkg = "com.example", kind = NodeKind.METHOD)

        val classC = node(id = 30L, fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.CLASS)
        val methodC = node(id = 31L, fqn = "com.example.C.methodC", name = "methodC", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(classA, methodA, classB, methodB, classC, methodC))

        // A зависит от B
        val metaA = NodeMeta(
            ownerFqn = classA.fqn,
            imports = listOf("com.example.B"),
            paramTypes = listOf("B"),
        )

        // B зависит от C
        val metaB = NodeMeta(
            ownerFqn = classB.fqn,
            imports = listOf("com.example.C"),
            paramTypes = listOf("C"),
        )

        // C зависит от A
        val metaC = NodeMeta(
            ownerFqn = classC.fqn,
            imports = listOf("com.example.A"),
            paramTypes = listOf("A"),
        )

        // when
        val edgesA = SignatureDependencyLinker().link(methodA, metaA, index)
        val edgesB = SignatureDependencyLinker().link(methodB, metaB, index)
        val edgesC = SignatureDependencyLinker().link(methodC, metaC, index)

        // then
        assertThat(edgesA).contains(Triple(classA, classB, EdgeKind.DEPENDS_ON))
        assertThat(edgesB).contains(Triple(classB, classC, EdgeKind.DEPENDS_ON))
        assertThat(edgesC).contains(Triple(classC, classA, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - множественные циклические зависимости через paramTypes и returnType`() {
        // given
        val classA = node(id = 10L, fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val methodA = node(id = 11L, fqn = "com.example.A.methodA", name = "methodA", pkg = "com.example", kind = NodeKind.METHOD)

        val classB = node(id = 20L, fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)
        val methodB = node(id = 21L, fqn = "com.example.B.methodB", name = "methodB", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(classA, methodA, classB, methodB))

        // methodA принимает B и возвращает B
        val metaA = NodeMeta(
            ownerFqn = classA.fqn,
            imports = listOf("com.example.B"),
            paramTypes = listOf("B"),
            returnType = "B",
        )

        // methodB принимает A и возвращает A
        val metaB = NodeMeta(
            ownerFqn = classB.fqn,
            imports = listOf("com.example.A"),
            paramTypes = listOf("A"),
            returnType = "A",
        )

        // when
        val edgesA = SignatureDependencyLinker().link(methodA, metaA, index)
        val edgesB = SignatureDependencyLinker().link(methodB, metaB, index)

        // then
        // A зависит от B (через param и return)
        assertThat(edgesA).contains(Triple(classA, classB, EdgeKind.DEPENDS_ON))
        // B зависит от A (через param и return)
        assertThat(edgesB).contains(Triple(classB, classA, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - циклическая зависимость через парсинг signature`() {
        // given
        val classA = node(id = 10L, fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.CLASS)
        val methodA = node(
            id = 11L,
            fqn = "com.example.A.methodA",
            name = "methodA",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun methodA(x: B): C",
        )

        val classB = node(id = 20L, fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.CLASS)
        val methodB = node(
            id = 21L,
            fqn = "com.example.B.methodB",
            name = "methodB",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun methodB(x: A): C",
        )

        val classC = node(id = 30L, fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(classA, methodA, classB, methodB, classC))

        // methodA использует B (из signature)
        val metaA = NodeMeta(imports = listOf("com.example.B", "com.example.C"))

        // methodB использует A (из signature)
        val metaB = NodeMeta(imports = listOf("com.example.A", "com.example.C"))

        // when
        val edgesA = SignatureDependencyLinker().link(methodA, metaA, index)
        val edgesB = SignatureDependencyLinker().link(methodB, metaB, index)

        // then
        assertThat(edgesA).contains(Triple(methodA, classB, EdgeKind.DEPENDS_ON))
        assertThat(edgesA).contains(Triple(methodA, classC, EdgeKind.DEPENDS_ON))
        assertThat(edgesB).contains(Triple(methodB, classA, EdgeKind.DEPENDS_ON))
        assertThat(edgesB).contains(Triple(methodB, classC, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - signature парсится с generic типами`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun doIt(x: List<Foo>, y: Map<String, Bar>): Baz<Qux>",
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val baz = node(id = 22L, fqn = "com.example.Baz", name = "Baz", pkg = "com.example", kind = NodeKind.CLASS)
        val qux = node(id = 23L, fqn = "com.example.Qux", name = "Qux", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo, bar, baz, qux))
        val meta = NodeMeta(imports = listOf("com.example.Foo", "com.example.Bar", "com.example.Baz", "com.example.Qux"))

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, foo, EdgeKind.DEPENDS_ON),
            Triple(fn, bar, EdgeKind.DEPENDS_ON),
            Triple(fn, baz, EdgeKind.DEPENDS_ON),
            Triple(fn, qux, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - signature парсится с nullable типами`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun doIt(x: Foo?, y: Bar): Baz?",
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)
        val baz = node(id = 22L, fqn = "com.example.Baz", name = "Baz", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo, bar, baz))
        val meta = NodeMeta(imports = listOf("com.example.Foo", "com.example.Bar", "com.example.Baz"))

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, foo, EdgeKind.DEPENDS_ON),
            Triple(fn, bar, EdgeKind.DEPENDS_ON),
            Triple(fn, baz, EdgeKind.DEPENDS_ON),
        )
    }

    @Test
    fun `link - когда meta paramTypes и returnType заполнены signature не используется`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun doIt(x: DifferentType): AnotherType",
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo, bar))
        val meta = NodeMeta(
            imports = listOf("com.example.Foo", "com.example.Bar"),
            paramTypes = listOf("Foo"),
            returnType = "Bar",
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(fn, foo, EdgeKind.DEPENDS_ON),
            Triple(fn, bar, EdgeKind.DEPENDS_ON),
        )
        // DifferentType и AnotherType из signature не используются
    }

    @Test
    fun `link - когда только paramTypes заполнены returnType пустой signature не используется`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun doIt(x: DifferentType): AnotherType",
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo))
        val meta = NodeMeta(
            imports = listOf("com.example.Foo"),
            paramTypes = listOf("Foo"),
            returnType = null,
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(fn, foo, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - когда только returnType заполнен paramTypes пустые signature не используется`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "fun doIt(x: DifferentType): AnotherType",
        )
        val bar = node(id = 21L, fqn = "com.example.Bar", name = "Bar", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, bar))
        val meta = NodeMeta(
            imports = listOf("com.example.Bar"),
            paramTypes = emptyList(),
            returnType = "Bar",
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(fn, bar, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - когда paramTypes и returnType null но signature blank signature парсится`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
            signature = "   ",
        )
        val index = NodeIndexFactory().create(listOf(fn))
        val meta = NodeMeta(
            imports = emptyList(),
            paramTypes = null,
            returnType = null,
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - null imports использует пустой список`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo))
        val meta = NodeMeta(
            imports = null,
            paramTypes = listOf("Foo"),
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(fn, foo, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - null packageName использует пустую строку`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.doIt",
            name = "doIt",
            pkg = null,
            kind = NodeKind.METHOD,
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo))
        val meta = NodeMeta(
            imports = listOf("com.example.Foo"),
            paramTypes = listOf("Foo"),
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(fn, foo, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - ownerFqn разрешается и используется как source вместо node`() {
        val owner = node(id = 10L, fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val fn = node(
            id = 11L,
            fqn = "com.example.Service.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(owner, fn, foo))
        val meta = NodeMeta(
            ownerFqn = owner.fqn,
            imports = listOf("com.example.Foo"),
            paramTypes = listOf("Foo"),
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(owner, foo, EdgeKind.DEPENDS_ON))
        assertThat(edges).doesNotContain(Triple(fn, foo, EdgeKind.DEPENDS_ON))
    }

    @Test
    fun `link - когда ownerFqn не найден используется node как source`() {
        val fn = node(
            id = 11L,
            fqn = "com.example.Service.doIt",
            name = "doIt",
            pkg = "com.example",
            kind = NodeKind.METHOD,
        )
        val foo = node(id = 20L, fqn = "com.example.Foo", name = "Foo", pkg = "com.example", kind = NodeKind.CLASS)

        val index = NodeIndexFactory().create(listOf(fn, foo))
        val meta = NodeMeta(
            ownerFqn = "com.example.NonExistentOwner",
            imports = listOf("com.example.Foo"),
            paramTypes = listOf("Foo"),
        )

        val edges = SignatureDependencyLinker().link(fn, meta, index)

        assertThat(edges).containsExactly(Triple(fn, foo, EdgeKind.DEPENDS_ON))
    }

    private fun node(
        id: Long,
        fqn: String,
        name: String,
        pkg: String?,
        kind: NodeKind,
        signature: String? = null,
    ): Node =
        Node(
            id = id,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
            signature = signature,
        )
}

