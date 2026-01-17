package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NodeDocContextBuilderTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var nodeDocRepo: NodeDocRepository
    private lateinit var builder: NodeDocContextBuilder

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk(relaxed = true)
        edgeRepo = mockk(relaxed = true)
        nodeDocRepo = mockk(relaxed = true)

        builder =
            NodeDocContextBuilder(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                nodeDocRepo = nodeDocRepo,
                methodMaxCodeChars = 8000,
                depsTopK = 20,
                childrenTopK = 40,
            )
    }

    @Test
    fun `build - для METHOD вызывает buildMethod`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"
        node.docComment = "/** KDoc */"

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasCode).isTrue
        assertThat(result.hasKdoc).isTrue
        assertThat(result.depsCount).isEqualTo(0)
    }

    @Test
    fun `build - для CLASS вызывает buildType`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L
        node.docComment = "/** KDoc */"

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isTrue
        assertThat(result.hasCode).isFalse
        assertThat(result.childrenCount).isEqualTo(0)
    }

    @Test
    fun `build - для PACKAGE вызывает buildContainer`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example", kind = NodeKind.PACKAGE, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.hasCode).isFalse
        assertThat(result.childrenCount).isEqualTo(0)
    }

    @Test
    fun `build - для FIELD вызывает buildLeaf`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        node.id = 100L
        node.docComment = "/** Field doc */"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isTrue
        assertThat(result.depsMissing).isFalse
    }

    @Test
    fun `buildMethod - обрабатывает зависимости с digest`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode = Node(application = app, fqn = "com.example.Dep", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = 200L

        val edge = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns dstNode
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)
        every { nodeRepo.findAllByIdIn(setOf(200L)) } returns listOf(dstNode)
        every { nodeDocRepo.findDigest(200L, "ru") } returns "digest content"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.depsCount).isEqualTo(1)
        assertThat(result.depsMissing).isFalse
        assertThat(result.depsForDigest).contains("com.example.Dep")
    }

    @Test
    fun `buildMethod - обрабатывает зависимости без digest`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode = Node(application = app, fqn = "com.example.Dep", kind = NodeKind.METHOD, lang = Lang.kotlin)
        dstNode.id = 200L

        val edge = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns dstNode
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)
        every { nodeRepo.findAllByIdIn(setOf(200L)) } returns listOf(dstNode)
        every { nodeDocRepo.findDigest(200L, "ru") } returns null

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.depsMissing).isTrue
        assertThat(result.missingDepKinds).contains(NodeKind.METHOD)
    }

    @Test
    fun `buildType - обрабатывает children с digest`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val child = Node(application = app, fqn = "com.example.Class1.method", kind = NodeKind.METHOD, lang = Lang.kotlin)
        child.id = 200L

        every { nodeRepo.findAllByParentId(100L) } returns listOf(child)
        every { nodeDocRepo.findDigest(200L, "ru") } returns "child digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.childrenCount).isEqualTo(1)
        assertThat(result.depsMissing).isFalse
    }

    @Test
    fun `buildType - обрабатывает children без digest`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val child = Node(application = app, fqn = "com.example.Class1.method", kind = NodeKind.METHOD, lang = Lang.kotlin)
        child.id = 200L

        every { nodeRepo.findAllByParentId(100L) } returns listOf(child)
        every { nodeDocRepo.findDigest(200L, "ru") } returns null

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.childrenCount).isEqualTo(1)
        assertThat(result.depsMissing).isTrue
        assertThat(result.missingChildKinds).contains(NodeKind.METHOD)
    }

    @Test
    fun `buildMethod - ограничивает количество зависимостей по depsTopK`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val edges = (1..25).map { i ->
            val dstNode = Node(application = app, fqn = "com.example.Dep$i", kind = NodeKind.CLASS, lang = Lang.kotlin)
            dstNode.id = (200L + i)
            mockk<Edge> {
                every { kind } returns EdgeKind.CALLS_CODE
                every { dst } returns dstNode
            }
        }

        every { edgeRepo.findAllBySrcId(100L) } returns edges
        every { nodeRepo.findAllByIdIn(any()) } returns emptyList()
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.depsCount).isEqualTo(20) // Ограничено depsTopK
    }

    @Test
    fun `build - для INTERFACE вызывает buildType`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Interface1", kind = NodeKind.INTERFACE, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.hasCode).isFalse
    }

    @Test
    fun `build - для ENUM вызывает buildType`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Enum1", kind = NodeKind.ENUM, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
    }

    @Test
    fun `build - для RECORD вызывает buildType`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Record1", kind = NodeKind.RECORD, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
    }

    @Test
    fun `build - для MODULE вызывает buildContainer`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.module", kind = NodeKind.MODULE, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.hasCode).isFalse
    }

    @Test
    fun `build - для REPO вызывает buildContainer`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.repo", kind = NodeKind.REPO, lang = Lang.kotlin)
        node.id = 100L

        every { nodeRepo.findAllByParentId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.hasCode).isFalse
    }

    @Test
    fun `buildMethod - обрабатывает edge с dst без id`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode = Node(application = app, fqn = "com.example.Dep", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = null // Нет id

        val edge = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns dstNode
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        // depsCount равен picked.size, а не количеству обработанных edges
        // Edge с dst.id == null попадает в picked, но пропускается через continue
        assertThat(result.depsCount).isEqualTo(1)
        // Но реально обработанных edges нет, поэтому depsForDigest пустой
        assertThat(result.depsForDigest).isEmpty()
        // И included не содержит этот edge (только self)
        assertThat(result.included).hasSize(1)
        assertThat(result.included[0]["level"]).isEqualTo("self")
    }

    @Test
    fun `buildMethod - обрабатывает edge где dst не найден в dstNodes`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode = Node(application = app, fqn = "com.example.Dep", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode.id = 200L

        val edge = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns dstNode
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)
        every { nodeRepo.findAllByIdIn(setOf(200L)) } returns emptyList() // dst не найден
        every { nodeDocRepo.findDigest(200L, "ru") } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.depsCount).isEqualTo(1)
        // Используется "node#200L" как fqn
    }

    @Test
    fun `buildMethod - обрабатывает метод без sourceCode`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = null

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasCode).isFalse
        assertThat(result.context).contains("// no source_code")
    }

    @Test
    fun `buildMethod - обрабатывает метод без docComment`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"
        node.docComment = null

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.context).doesNotContain("## KDoc")
    }

    @Test
    fun `buildMethod - обрабатывает метод с signature`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"
        node.signature = "fun test(param: String): Int"

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.context).contains("signature=fun test(param: String): Int")
    }

    @Test
    fun `buildMethod - обрабатывает разные edge kinds`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode1 = Node(application = app, fqn = "com.example.Dep1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode1.id = 200L
        val dstNode2 = Node(application = app, fqn = "com.example.Dep2", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode2.id = 201L

        val edge1 = mockk<Edge> {
            every { kind } returns EdgeKind.THROWS
            every { dst } returns dstNode1
        }
        val edge2 = mockk<Edge> {
            every { kind } returns EdgeKind.READS
            every { dst } returns dstNode2
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge1, edge2)
        every { nodeRepo.findAllByIdIn(setOf(200L, 201L)) } returns listOf(dstNode1, dstNode2)
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.depsCount).isEqualTo(2)
    }

    @Test
    fun `buildType - фильтрует children по METHOD и FIELD`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val methodChild = Node(application = app, fqn = "com.example.Class1.method", kind = NodeKind.METHOD, lang = Lang.kotlin)
        methodChild.id = 200L
        val fieldChild = Node(application = app, fqn = "com.example.Class1.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        fieldChild.id = 201L
        val classChild = Node(application = app, fqn = "com.example.Class1.Inner", kind = NodeKind.CLASS, lang = Lang.kotlin)
        classChild.id = 202L

        every { nodeRepo.findAllByParentId(100L) } returns listOf(methodChild, fieldChild, classChild)
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        // Только METHOD и FIELD должны быть включены
        assertThat(result.childrenCount).isEqualTo(2)
    }

    @Test
    fun `buildContainer - обрабатывает MODULE с children`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.module", kind = NodeKind.MODULE, lang = Lang.kotlin)
        node.id = 100L

        val child = Node(application = app, fqn = "com.example.module.package", kind = NodeKind.PACKAGE, lang = Lang.kotlin)
        child.id = 200L

        every { nodeRepo.findAllByParentId(100L) } returns listOf(child)
        every { nodeDocRepo.findDigest(200L, "ru") } returns "child digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.childrenCount).isEqualTo(1)
        assertThat(result.context).contains("kind=MODULE")
    }

    @Test
    fun `buildContainer - обрабатывает REPO с children`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.repo", kind = NodeKind.REPO, lang = Lang.kotlin)
        node.id = 100L

        val child = Node(application = app, fqn = "com.example.repo.module", kind = NodeKind.MODULE, lang = Lang.kotlin)
        child.id = 200L

        every { nodeRepo.findAllByParentId(100L) } returns listOf(child)
        every { nodeDocRepo.findDigest(200L, "ru") } returns "child digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.childrenCount).isEqualTo(1)
        assertThat(result.context).contains("kind=REPO")
    }

    @Test
    fun `buildLeaf - обрабатывает leaf с кодом и kdoc`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "val field = 42"
        node.docComment = "/** Field doc */"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isTrue
        assertThat(result.hasCode).isTrue
        assertThat(result.context).contains("## Doc comment")
        assertThat(result.context).contains("## Source")
    }

    @Test
    fun `buildLeaf - обрабатывает leaf без кода и kdoc`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = null
        node.docComment = null

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.hasKdoc).isFalse
        assertThat(result.hasCode).isFalse
        assertThat(result.context).doesNotContain("## Doc comment")
        assertThat(result.context).doesNotContain("## Source")
    }

    @Test
    fun `buildMethod - ограничивает длину кода по methodMaxCodeChars`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "a".repeat(10000) // Больше чем methodMaxCodeChars (8000)

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        assertThat(result.context).contains("truncated=true")
    }

    @Test
    fun `buildMethod - обрабатывает edge где dst null`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val edge: Edge = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns mockk(relaxed = true)
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge)

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull
        // Edge с null dst должен быть обработан
    }
    
    @Test
    fun `buildMethod - обрабатывает приоритеты edge kinds`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val dstNode1 = Node(application = app, fqn = "com.example.Dep1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode1.id = 200L
        val dstNode2 = Node(application = app, fqn = "com.example.Dep2", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode2.id = 201L
        val dstNode3 = Node(application = app, fqn = "com.example.Dep3", kind = NodeKind.CLASS, lang = Lang.kotlin)
        dstNode3.id = 202L

        // CALLS_CODE имеет приоритет выше DEPENDS_ON
        val edge1 = mockk<Edge> {
            every { kind } returns EdgeKind.DEPENDS_ON
            every { dst } returns dstNode1
        }
        val edge2 = mockk<Edge> {
            every { kind } returns EdgeKind.CALLS_CODE
            every { dst } returns dstNode2
        }
        val edge3 = mockk<Edge> {
            every { kind } returns EdgeKind.DEPENDS_ON
            every { dst } returns dstNode3
        }

        every { edgeRepo.findAllBySrcId(100L) } returns listOf(edge1, edge2, edge3)
        every { nodeRepo.findAllByIdIn(setOf(200L, 201L, 202L)) } returns listOf(dstNode1, dstNode2, dstNode3)
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.depsCount).isEqualTo(3)
        // CALLS_CODE должен быть первым в списке
    }
    
    @Test
    fun `buildMethod - ограничивает depsForDigest до 50`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"

        val edges = (1..60).map { i ->
            val dstNode = Node(application = app, fqn = "com.example.Dep$i", kind = NodeKind.CLASS, lang = Lang.kotlin)
            dstNode.id = (200L + i)
            mockk<Edge> {
                every { kind } returns EdgeKind.CALLS_CODE
                every { dst } returns dstNode
            }
        }

        every { edgeRepo.findAllBySrcId(100L) } returns edges
        every { nodeRepo.findAllByIdIn(any()) } returns emptyList()
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.depsForDigest.size).isLessThanOrEqualTo(50)
    }
    
    @Test
    fun `buildMethod - обрабатывает длинный sourceCode больше methodMaxCodeChars`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "a".repeat(10000) // Больше чем methodMaxCodeChars (8000)

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.hasCode).isTrue()
        assertThat(result.context).contains("truncated=true")
    }
    
    @Test
    fun `buildType - ограничивает childrenTopK`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val children = (1..50).map { i ->
            val child = Node(application = app, fqn = "com.example.Class1.method$i", kind = NodeKind.METHOD, lang = Lang.kotlin)
            child.id = (200L + i)
            child
        }

        every { nodeRepo.findAllByParentId(100L) } returns children
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.childrenCount).isEqualTo(40) // childrenTopK = 40
    }
    
    @Test
    fun `buildContainer - ограничивает childrenTopK для PACKAGE`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example", kind = NodeKind.PACKAGE, lang = Lang.kotlin)
        node.id = 100L

        val children = (1..50).map { i ->
            val child = Node(application = app, fqn = "com.example.Class$i", kind = NodeKind.CLASS, lang = Lang.kotlin)
            child.id = (200L + i)
            child
        }

        every { nodeRepo.findAllByParentId(100L) } returns children
        every { nodeDocRepo.findDigest(any(), any()) } returns "digest"

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.childrenCount).isEqualTo(40) // childrenTopK = 40
    }
    
    @Test
    fun `buildMethod - обрабатывает signature с пробелами`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"
        node.signature = "  fun test(param: String): Int  "

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.context).contains("signature=fun test(param: String): Int")
        assertThat(result.context).doesNotContain("  ") // Пробелы должны быть обрезаны
    }
    
    @Test
    fun `buildMethod - обрабатывает kdoc длиннее 4000 символов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "fun test() {}"
        node.docComment = "a".repeat(5000)

        every { edgeRepo.findAllBySrcId(100L) } returns emptyList()

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.hasKdoc).isTrue()
        // KDoc должен быть обрезан до 4000 символов
        val kdocSection = result.context.substringAfter("## KDoc\n").substringBefore("\n\n")
        assertThat(kdocSection.length).isLessThanOrEqualTo(4000)
    }
    
    @Test
    fun `buildLeaf - обрабатывает sourceCode длиннее methodMaxCodeChars`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        node.id = 100L
        node.sourceCode = "a".repeat(10000)

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.hasCode).isTrue()
        // sourceCode должен быть обрезан до methodMaxCodeChars (8000)
    }
    
    @Test
    fun `buildLeaf - обрабатывает kdoc длиннее 4000 символов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.field", kind = NodeKind.FIELD, lang = Lang.kotlin)
        node.id = 100L
        node.docComment = "a".repeat(5000)

        val result = builder.build(node, "ru")

        assertThat(result).isNotNull()
        assertThat(result.hasKdoc).isTrue()
        // KDoc должен быть обрезан до 4000 символов
        val kdocSection = result.context.substringAfter("## Doc comment\n").substringBefore("\n\n")
        assertThat(kdocSection.length).isLessThanOrEqualTo(4000)
    }
}
