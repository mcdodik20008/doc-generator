package com.bftcom.docgenerator.graph.impl.node.builder

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.node.CodeHasher
import com.bftcom.docgenerator.graph.api.node.CodeNormalizer
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import com.bftcom.docgenerator.graph.api.node.NodeValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NodeBuilderTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var validator: NodeValidator
    private lateinit var codeNormalizer: CodeNormalizer
    private lateinit var codeHasher: CodeHasher
    private lateinit var updateStrategy: NodeUpdateStrategy
    private lateinit var builder: NodeBuilder

    private val app = Application(id = 1L, key = "app", name = "App")

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk()
        objectMapper = ObjectMapper().registerKotlinModule()
        validator = mockk(relaxed = true)
        codeNormalizer = mockk()
        codeHasher = mockk()
        updateStrategy = mockk()

        builder =
            NodeBuilder(
                application = app,
                nodeRepo = nodeRepo,
                objectMapper = objectMapper,
                validator = validator,
                codeNormalizer = codeNormalizer,
                codeHasher = codeHasher,
                updateStrategy = updateStrategy,
            )
    }

    @Test
    fun `upsertNode - создает новую ноду и вычисляет lineEnd по countLines`() {
        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.example.Foo") } returns null
        every { codeNormalizer.normalize(any(), any()) } answers { firstArg() }
        every { codeNormalizer.countLines(any()) } returns 3
        every { codeHasher.computeHash(any()) } returns "hash"
        every { nodeRepo.save(any()) } answers {
            val n = firstArg<Node>()
            n.id = 123L
            n
        }

        val saved =
            builder.upsertNode(
                fqn = "com.example.Foo",
                kind = NodeKind.CLASS,
                name = "Foo",
                packageName = "com.example",
                parent = null,
                lang = Lang.kotlin,
                filePath = "/tmp/Foo.kt",
                span = 10..10,
                signature = null,
                sourceCode = "line1\nline2\nline3\n",
                docComment = "/** kdoc */",
                meta = NodeMeta(source = "onType", pkgFqn = "com.example"),
            )

        assertThat(saved.id).isEqualTo(123L)
        assertThat(saved.lineStart).isEqualTo(10)
        assertThat(saved.lineEnd).isEqualTo(12) // 10 + 3 - 1
        assertThat(saved.codeHash).isEqualTo("hash")
        assertThat(saved.sourceCode).contains("line1")
        assertThat(builder.getStats().created).isEqualTo(1)

        verify(exactly = 1) { validator.validate("com.example.Foo", any(), any(), any(), 1L) }
        verify(exactly = 1) { nodeRepo.save(any()) }
    }

    @Test
    fun `upsertNode - если изменений нет, не сохраняет и увеличивает skipped`() {
        val existing =
            Node(
                id = 10L,
                application = app,
                fqn = "com.example.Foo",
                name = "Foo",
                packageName = "com.example",
                kind = NodeKind.CLASS,
                lang = Lang.kotlin,
                meta = emptyMap(),
            )

        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.example.Foo") } returns existing
        every { codeNormalizer.normalize(any(), any()) } answers { firstArg() }
        every { codeHasher.computeHash(any()) } returns "hash"
        every { updateStrategy.hasChanges(any(), any()) } returns false

        val returned =
            builder.upsertNode(
                fqn = "com.example.Foo",
                kind = NodeKind.CLASS,
                name = "Foo",
                packageName = "com.example",
                parent = null,
                lang = Lang.kotlin,
                filePath = null,
                span = null,
                signature = null,
                sourceCode = null,
                docComment = null,
                meta = NodeMeta(),
            )

        assertThat(returned).isSameAs(existing)
        assertThat(builder.getStats().skipped).isEqualTo(1)
        verify(exactly = 0) { nodeRepo.save(any()) }
    }

    @Test
    fun `upsertNode - обновляет существующую ноду когда есть изменения`() {
        val existing =
            Node(
                id = 10L,
                application = app,
                fqn = "com.example.Foo",
                name = "Foo",
                packageName = "com.example",
                kind = NodeKind.CLASS,
                lang = Lang.kotlin,
                meta = emptyMap(),
            )

        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.example.Foo") } returns existing
        every { codeNormalizer.normalize(any(), any()) } answers { firstArg() }
        every { codeNormalizer.countLines(any()) } returns 1
        every { codeHasher.computeHash(any()) } returns "hash"
        every { updateStrategy.hasChanges(existing, any()) } returns true
        every { updateStrategy.update(existing, any()) } answers { firstArg() }
        every { nodeRepo.save(any()) } answers { firstArg<Node>() }

        val saved =
            builder.upsertNode(
                fqn = "com.example.Foo",
                kind = NodeKind.CLASS,
                name = "Foo",
                packageName = "com.example",
                parent = null,
                lang = Lang.kotlin,
                filePath = "/tmp/Foo.kt",
                span = 1..1,
                signature = null,
                sourceCode = "class Foo",
                docComment = null,
                meta = NodeMeta(source = "onType", pkgFqn = "com.example"),
            )

        assertThat(saved).isSameAs(existing)
        assertThat(builder.getStats().updated).isEqualTo(1)
        verify(exactly = 1) { nodeRepo.save(any()) }
        verify(exactly = 1) { updateStrategy.update(existing, any()) }
    }

    @Test
    fun `upsertNode - не пересчитывает lineEnd когда нормализованный код пустой`() {
        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.example.Empty") } returns null
        every { codeNormalizer.normalize(any(), any()) } returns ""
        every { codeHasher.computeHash(any()) } returns "hash"
        every { nodeRepo.save(any()) } answers {
            val n = firstArg<Node>()
            n.id = 5L
            n
        }

        val saved =
            builder.upsertNode(
                fqn = "com.example.Empty",
                kind = NodeKind.CLASS,
                name = "Empty",
                packageName = "com.example",
                parent = null,
                lang = Lang.kotlin,
                filePath = "/tmp/Empty.kt",
                span = 5..8,
                signature = null,
                sourceCode = "",
                docComment = null,
                meta = NodeMeta(source = "onType", pkgFqn = "com.example"),
            )

        assertThat(saved.lineEnd).isEqualTo(8)
        verify(exactly = 0) { codeNormalizer.countLines(any()) }
    }

    @Test
    fun `upsertNode - создает ноду даже если id не установлен`() {
        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.example.NoId") } returns null
        every { codeNormalizer.normalize(any(), any()) } answers { firstArg() }
        every { codeNormalizer.countLines(any()) } returns 1
        every { codeHasher.computeHash(any()) } returns "hash"
        every { nodeRepo.save(any()) } answers { firstArg<Node>() }

        val saved =
            builder.upsertNode(
                fqn = "com.example.NoId",
                kind = NodeKind.CLASS,
                name = "NoId",
                packageName = "com.example",
                parent = null,
                lang = Lang.kotlin,
                filePath = "/tmp/NoId.kt",
                span = 1..1,
                signature = null,
                sourceCode = "class NoId",
                docComment = null,
                meta = NodeMeta(source = "onType", pkgFqn = "com.example"),
            )

        assertThat(saved.id).isNull()
        assertThat(builder.getStats().created).isEqualTo(1)
    }
}

