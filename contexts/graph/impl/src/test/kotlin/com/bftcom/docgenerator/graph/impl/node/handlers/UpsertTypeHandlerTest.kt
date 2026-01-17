package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.declplanner.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.LineSpan
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UpsertTypeHandlerTest {
    private val nodeKindRefiner = mockk<NodeKindRefiner>(relaxed = true)
    private val apiMetadataCollector = mockk<ApiMetadataCollector>(relaxed = true)
    private val handler = UpsertTypeHandler(nodeKindRefiner, apiMetadataCollector)
    private val handlerWithoutCollector = UpsertTypeHandler(nodeKindRefiner, null)
    private val state = mockk<GraphState>(relaxed = true)
    private val builder = mockk<NodeBuilder>(relaxed = true)
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `handle should use pkgFqn from raw when available`() {
        val raw = createRawType(pkgFqn = "com.example")
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getOrPutPackage(any(), any()) } returns createPackageNode("com.example")
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("com.example.TestClass")

        handler.handle(cmd, state, builder)

        verify { state.getOrPutPackage("com.example", any()) }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = "com.example",
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should use file package when pkgFqn is null`() {
        val raw = createRawType(pkgFqn = null)
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns "com.example"
        every { state.getOrPutPackage(any(), any()) } returns createPackageNode("com.example")
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("com.example.TestClass")

        handler.handle(cmd, state, builder)

        verify { state.getFilePackage(raw.filePath) }
        verify { state.getOrPutPackage("com.example", any()) }
    }

    @Test
    fun `handle should use empty string when pkgFqn and file package are null`() {
        val raw = createRawType(pkgFqn = null)
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify { state.getFilePackage(raw.filePath) }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = null,
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should not create package when pkgFqn is blank`() {
        val raw = createRawType(pkgFqn = "")
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify(exactly = 0) { state.getOrPutPackage(any(), any()) }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = null,
                parent = null,
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should create package when pkgFqn is not blank`() {
        val raw = createRawType(pkgFqn = "com.example")
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)
        val pkgNode = createPackageNode("com.example")

        every { state.getFilePackage(any()) } returns null
        every { state.getOrPutPackage(any(), any()) } answers {
            val factory = secondArg<() -> Node>()
            factory()
        }
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returnsMany listOf(pkgNode, createTypeNode("com.example.TestClass"))

        handler.handle(cmd, state, builder)

        verify { state.getOrPutPackage("com.example", any()) }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = "com.example",
                parent = pkgNode,
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("spanTestCases")
    fun `handle should process span correctly`(span: LineSpan?, expectedSpan: IntRange?) {
        val raw = createRawType(span = span)
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = expectedSpan,
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = expectedSpan,
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should extract signature from attributes when present`() {
        val raw = createRawType(attributes = mapOf("signature" to "class TestClass"))
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = "class TestClass",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = "class TestClass",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should use null signature when not in attributes`() {
        val raw = createRawType(attributes = emptyMap())
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = null,
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = null,
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should call apiMetadataCollector when present`() {
        val raw = createRawType()
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns listOf("java.util.List")
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify { apiMetadataCollector.extractTypeMetadata(raw, any()) }
    }

    @Test
    fun `handle should not call apiMetadataCollector when null`() {
        val raw = createRawType()
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createTypeNode("TestClass")

        handlerWithoutCollector.handle(cmd, state, builder)

        verify(exactly = 0) { apiMetadataCollector.extractTypeMetadata(any(), any()) }
    }

    @Test
    fun `handle should call nodeKindRefiner forType`() {
        val raw = createRawType()
        val fileUnit = createRawFileUnit()
        val cmd = UpsertTypeCmd(raw, NodeKind.INTERFACE)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns fileUnit
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.INTERFACE
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify { nodeKindRefiner.forType(NodeKind.INTERFACE, raw, fileUnit) }
    }

    @Test
    fun `handle should put type in state after creation`() {
        val raw = createRawType()
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)
        val typeNode = createTypeNode("com.example.TestClass")

        every { state.getFilePackage(any()) } returns "com.example"
        every { state.getOrPutPackage(any(), any()) } returns createPackageNode("com.example")
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns typeNode

        handler.handle(cmd, state, builder)

        verify { state.putType("com.example.TestClass", typeNode) }
    }

    @Test
    fun `handle should use blank pkgFqn correctly in meta`() {
        val raw = createRawType(pkgFqn = "")
        val cmd = UpsertTypeCmd(raw, NodeKind.CLASS)

        every { state.getFilePackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns listOf("java.util.List")
        every { nodeKindRefiner.forType(any(), any(), any()) } returns NodeKind.CLASS
        every { apiMetadataCollector.extractTypeMetadata(any(), any()) } returns null
        every {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createTypeNode("TestClass")

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = null,
                parent = any(),
                lang = any(),
                filePath = any(),
                span = any(),
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = match { it.pkgFqn == null },
            )
        }
    }

    companion object {
        @JvmStatic
        fun spanTestCases(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LineSpan(1, 10), 1..10),
                Arguments.of(null, null),
            )
        }
    }

    private fun createRawType(
        pkgFqn: String? = "com.example",
        span: LineSpan? = null,
        attributes: Map<String, Any?> = emptyMap(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = "TestClass",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = span,
            text = "class TestClass",
            attributes = attributes,
        )
    }

    private fun createTypeNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() },
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            parent = null,
            filePath = null,
            lineStart = null,
            lineEnd = null,
            sourceCode = null,
            docComment = null,
            signature = null,
            codeHash = null,
            meta = emptyMap(),
        )
    }

    private fun createPackageNode(pkgFqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = pkgFqn,
            name = pkgFqn.substringAfterLast('.'),
            packageName = pkgFqn,
            kind = NodeKind.PACKAGE,
            lang = Lang.kotlin,
            parent = null,
            filePath = null,
            lineStart = null,
            lineEnd = null,
            sourceCode = null,
            docComment = null,
            signature = null,
            codeHash = null,
            meta = emptyMap(),
        )
    }

    private fun createRawFileUnit(): RawFileUnit {
        return RawFileUnit(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            imports = emptyList(),
        )
    }
}
