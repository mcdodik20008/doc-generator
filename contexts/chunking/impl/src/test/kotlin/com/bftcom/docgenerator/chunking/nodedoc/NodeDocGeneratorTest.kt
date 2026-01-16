package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.ai.chatclients.NodeDocDigestClient
import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.ai.prompts.NodeDocContextProfile
import com.bftcom.docgenerator.ai.prompts.NodeDocPrompt
import com.bftcom.docgenerator.ai.prompts.NodeDocPromptRegistry
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NodeDocGeneratorTest {
    private lateinit var contextBuilder: NodeDocContextBuilder
    private lateinit var nodeDocRepo: NodeDocRepository
    private lateinit var coder: OllamaCoderClient
    private lateinit var digestClient: NodeDocDigestClient
    private lateinit var talker: OllamaTalkerClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var promptRegistry: NodeDocPromptRegistry
    private lateinit var generator: NodeDocGenerator

    @BeforeEach
    fun setUp() {
        contextBuilder = mockk(relaxed = true)
        nodeDocRepo = mockk(relaxed = true)
        coder = mockk(relaxed = true)
        digestClient = mockk(relaxed = true)
        talker = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerKotlinModule()
        promptRegistry = mockk(relaxed = true)

        generator =
            NodeDocGenerator(
                contextBuilder = contextBuilder,
                nodeDocRepo = nodeDocRepo,
                coder = coder,
                digestClient = digestClient,
                talker = talker,
                objectMapper = objectMapper,
                promptRegistry = promptRegistry,
            )
    }

    @Test
    fun `generate - успешно генерирует документ для метода`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.codeHash = "hash123"
        node.docComment = "KDoc comment"

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = false,
                depsForDigest = listOf("dep1", "dep2"),
                included = emptyList(),
                depsCount = 2,
                childrenCount = 0,
                hasKdoc = true,
                hasCode = true,
                missingDepKinds = emptySet(),
                missingChildKinds = emptySet(),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every {
            promptRegistry.resolve(
                NodeDocContextProfile(
                    kind = NodeKind.METHOD,
                    hasKdoc = true,
                    hasCode = true,
                    depsCount = 2,
                    childrenCount = 0,
                ),
            )
        } returns prompt
        every { coder.generate("context", "Generate doc") } returns "tech doc content"
        every {
            talker.rewrite(
                TalkerRewriteRequest(
                    nodeFqn = "com.example.Method1",
                    language = "ru",
                    rawContent = "tech doc content",
                ),
            )
        } returns "public doc content"
        every {
            digestClient.generate("METHOD", "com.example.Method1", "tech doc content", listOf("dep1", "dep2"))
        } returns "digest123"

        val result = generator.generate(node, "ru", false)

        assertThat(result).isNotNull
        assertThat(result!!.docTech).isEqualTo("tech doc content")
        assertThat(result.docPublic).isEqualTo("public doc content")
        assertThat(result.docDigest).isEqualTo("digest123")
        assertThat(result.modelMeta["prompt_id"]).isEqualTo("prompt1")
        assertThat(result.modelMeta["deps_missing"]).isEqualTo(false)
    }

    @Test
    fun `generate - возвращает null когда missing deps и allowMissingDeps=false`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = true,
                missingDepKinds = setOf(NodeKind.METHOD),
                missingChildKinds = emptySet(),
            )

        every { contextBuilder.build(node, "ru") } returns buildResult

        val result = generator.generate(node, "ru", false)

        assertThat(result).isNull()
        verify(exactly = 0) { coder.generate(any(), any()) }
    }

    @Test
    fun `generate - генерирует документ когда allowMissingDeps=true`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = true,
                missingDepKinds = setOf(NodeKind.METHOD),
                missingChildKinds = emptySet(),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every {
            promptRegistry.resolve(
                NodeDocContextProfile(
                    kind = NodeKind.METHOD,
                    hasKdoc = false,
                    hasCode = true,
                    depsCount = 0,
                    childrenCount = 0,
                ),
            )
        } returns prompt
        every { coder.generate("context", "Generate doc") } returns "tech doc"
        every {
            talker.rewrite(
                TalkerRewriteRequest(
                    nodeFqn = "com.example.Method1",
                    language = "ru",
                    rawContent = "tech doc",
                ),
            )
        } returns "public doc"
        every {
            digestClient.generate("METHOD", "com.example.Method1", "tech doc", emptyList())
        } returns "digest"

        val result = generator.generate(node, "ru", true)

        assertThat(result).isNotNull
        assertThat(result!!.modelMeta["deps_missing"]).isEqualTo(true)
    }

    @Test
    fun `store - сохраняет документ в репозиторий`() {
        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = mapOf("key" to "value"),
            )

        every {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = "public doc",
                docTech = "tech doc",
                docDigest = "digest",
                modelMetaJson = any(),
            )
        } returns 1

        generator.store(100L, "ru", generatedDoc)

        verify(exactly = 1) {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = "public doc",
                docTech = "tech doc",
                docDigest = "digest",
                modelMetaJson = any(),
            )
        }
    }

    @Test
    fun `store - сохраняет null для пустых строк`() {
        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "",
                docPublic = "  ",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = null,
                docTech = null,
                docDigest = "digest",
                modelMetaJson = any(),
            )
        } returns 1

        generator.store(100L, "ru", generatedDoc)

        verify(exactly = 1) {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = null,
                docTech = null,
                docDigest = "digest",
                modelMetaJson = any(),
            )
        }
    }

    @Test
    fun `generate - включает source_hashes в modelMeta`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.codeHash = "code_hash_123"
        node.docComment = "doc comment"

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = false,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = true,
                hasCode = true,
                missingDepKinds = emptySet(),
                missingChildKinds = emptySet(),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every {
            promptRegistry.resolve(any())
        } returns prompt
        every { coder.generate(any(), any()) } returns "tech doc"
        every { talker.rewrite(any()) } returns "public doc"
        every { digestClient.generate(any(), any(), any(), any()) } returns "digest"

        val result = generator.generate(node, "ru", false)

        assertThat(result).isNotNull
        val sourceHashes = result!!.modelMeta["source_hashes"] as Map<*, *>
        assertThat(sourceHashes["code_hash"]).isEqualTo("code_hash_123")
        assertThat(sourceHashes["doc_comment_hash"]).isNotNull()
    }

    @Test
    fun `generate - обрабатывает CLASS с missing deps и allowMissingDeps=false`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = false,
                missingDepKinds = emptySet(),
                missingChildKinds = setOf(NodeKind.METHOD),
            )

        every { contextBuilder.build(node, "ru") } returns buildResult

        val result = generator.generate(node, "ru", false)

        // Для CLASS должен вернуть null, так как missingChildKinds содержит METHOD
        assertThat(result).isNull()
        verify(exactly = 0) { coder.generate(any(), any()) }
    }

    @Test
    fun `generate - обрабатывает CLASS с missing deps и allowMissingDeps=true`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = false,
                missingDepKinds = emptySet(),
                missingChildKinds = setOf(NodeKind.METHOD),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every { promptRegistry.resolve(any()) } returns prompt
        every { coder.generate(any(), any()) } returns "tech doc"
        every { talker.rewrite(any()) } returns "public doc"
        every { digestClient.generate(any(), any(), any(), any()) } returns "digest"

        val result = generator.generate(node, "ru", true)

        assertThat(result).isNotNull
        assertThat(result!!.modelMeta["deps_missing"]).isEqualTo(true)
    }

    @Test
    fun `generate - обрабатывает METHOD с missing METHOD deps и allowMissingDeps=false`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = true,
                missingDepKinds = setOf(NodeKind.METHOD),
                missingChildKinds = emptySet(),
            )

        every { contextBuilder.build(node, "ru") } returns buildResult

        val result = generator.generate(node, "ru", false)

        // Для METHOD с missing METHOD deps должен вернуть null
        assertThat(result).isNull()
        verify(exactly = 0) { coder.generate(any(), any()) }
    }

    @Test
    fun `generate - обрабатывает METHOD с missing не-METHOD deps и allowMissingDeps=false`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = true,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = true,
                missingDepKinds = setOf(NodeKind.CLASS), // Не METHOD
                missingChildKinds = emptySet(),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every { promptRegistry.resolve(any()) } returns prompt
        every { coder.generate(any(), any()) } returns "tech doc"
        every { talker.rewrite(any()) } returns "public doc"
        every { digestClient.generate(any(), any(), any(), any()) } returns "digest"

        val result = generator.generate(node, "ru", false)

        // Для METHOD с missing не-METHOD deps должен генерировать
        assertThat(result).isNotNull
    }

    @Test
    fun `generate - обрабатывает node с null codeHash`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node.id = 100L
        node.codeHash = null
        node.docComment = null

        val buildResult =
            NodeDocContextBuilder.BuildResult(
                context = "context",
                depsMissing = false,
                depsForDigest = emptyList(),
                included = emptyList(),
                depsCount = 0,
                childrenCount = 0,
                hasKdoc = false,
                hasCode = true,
                missingDepKinds = emptySet(),
                missingChildKinds = emptySet(),
            )

        val prompt = NodeDocPrompt(id = "prompt1", systemPrompt = "Generate doc")

        every { contextBuilder.build(node, "ru") } returns buildResult
        every { promptRegistry.resolve(any()) } returns prompt
        every { coder.generate(any(), any()) } returns "tech doc"
        every { talker.rewrite(any()) } returns "public doc"
        every { digestClient.generate(any(), any(), any(), any()) } returns "digest"

        val result = generator.generate(node, "ru", false)

        assertThat(result).isNotNull
        val sourceHashes = result!!.modelMeta["source_hashes"] as Map<*, *>
        assertThat(sourceHashes["code_hash"]).isEqualTo("")
    }

    @Test
    fun `store - обрабатывает пустой docDigest`() {
        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "",
                modelMeta = emptyMap(),
            )

        every {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = "public doc",
                docTech = "tech doc",
                docDigest = null,
                modelMetaJson = any(),
            )
        } returns 1

        generator.store(100L, "ru", generatedDoc)

        verify(exactly = 1) {
            nodeDocRepo.upsert(
                nodeId = 100L,
                locale = "ru",
                docPublic = "public doc",
                docTech = "tech doc",
                docDigest = null,
                modelMetaJson = any(),
            )
        }
    }
}
