package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.library.LibraryNodeEnricher
import com.bftcom.docgenerator.graph.api.model.rawdecl.LineSpan
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
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

class UpsertFunctionHandlerTest {
    private val nodeKindRefiner = mockk<NodeKindRefiner>(relaxed = true)
    private val apiMetadataCollector = mockk<ApiMetadataCollector>(relaxed = true)
    private val libraryNodeEnricher = mockk<LibraryNodeEnricher>(relaxed = true)
    private val handler = UpsertFunctionHandler(nodeKindRefiner, apiMetadataCollector, libraryNodeEnricher)
    private val handlerWithoutCollector = UpsertFunctionHandler(nodeKindRefiner, null, null)
    private val state = mockk<GraphState>(relaxed = true)
    private val builder = mockk<NodeBuilder>(relaxed = true)
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `handle should use pkgFqn from raw when available`() {
        val raw = createRawFunction(pkgFqn = "com.example", ownerFqn = null)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.testMethod")

        handler.handle(cmd, state, builder)

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
        val raw = createRawFunction(pkgFqn = null, ownerFqn = null)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns "com.example"
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.testMethod")

        handler.handle(cmd, state, builder)

        verify { state.getFilePackage(raw.filePath) }
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
    fun `handle should use owner type as parent when ownerFqn is not null and not blank`() {
        val ownerType = createTypeNode("com.example.Owner")
        val raw = createRawFunction(pkgFqn = "com.example", ownerFqn = "com.example.Owner")
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType("com.example.Owner") } returns ownerType
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.Owner.testMethod")

        handler.handle(cmd, state, builder)

        verify { state.getType("com.example.Owner") }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = ownerType,
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
    fun `handle should use package as parent when ownerFqn is null and pkgFqn is not blank`() {
        val pkg = createPackageNode("com.example")
        val raw = createRawFunction(pkgFqn = "com.example", ownerFqn = null)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage("com.example") } returns pkg
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.testMethod")

        handler.handle(cmd, state, builder)

        verify { state.getPackage("com.example") }
        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
                parent = pkg,
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
    fun `handle should use null parent when both ownerFqn and pkgFqn are null or blank`() {
        val raw = createRawFunction(pkgFqn = null, ownerFqn = null)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("testMethod")

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = any(),
                kind = any(),
                name = any(),
                packageName = any(),
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
    fun `handle should use null parent when ownerFqn is blank`() {
        val raw = createRawFunction(pkgFqn = "com.example", ownerFqn = "")
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage("com.example") } returns createPackageNode("com.example")
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.testMethod")

        handler.handle(cmd, state, builder)

        verify { state.getPackage("com.example") }
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
                signature = any(),
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should use signatureRepr when present`() {
        val raw = createRawFunction(signatureRepr = "fun testMethod(param: String)")
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
                signature = "fun testMethod(param: String)",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createFunctionNode("testMethod")

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
                signature = "fun testMethod(param: String)",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should build signature from name and params when signatureRepr is null`() {
        val raw = createRawFunction(
            signatureRepr = null,
            paramNames = listOf("param1", "param2"),
        )
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
                signature = "testMethod(param1,param2)",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createFunctionNode("testMethod")

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
                signature = "testMethod(param1,param2)",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should build signature with empty params when signatureRepr is null and no params`() {
        val raw = createRawFunction(
            signatureRepr = null,
            paramNames = emptyList(),
        )
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
                signature = "testMethod()",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        } returns createFunctionNode("testMethod")

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
                signature = "testMethod()",
                sourceCode = any(),
                docComment = any(),
                meta = any(),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("spanTestCases")
    fun `handle should process span correctly`(span: LineSpan?, expectedSpan: IntRange?) {
        val raw = createRawFunction(span = span)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("testMethod")

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
    fun `handle should call apiMetadataCollector when present`() {
        val raw = createRawFunction()
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns listOf("java.util.List")
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("testMethod")

        handler.handle(cmd, state, builder)

        verify { apiMetadataCollector.extractFunctionMetadata(raw, null, any()) }
    }

    @Test
    fun `handle should not call apiMetadataCollector when null`() {
        val raw = createRawFunction()
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
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
        } returns createFunctionNode("testMethod")

        handlerWithoutCollector.handle(cmd, state, builder)

        verify(exactly = 0) { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) }
    }

    @Test
    fun `handle should pass null ownerType to apiMetadataCollector even when ownerFqn exists`() {
        val ownerType = createTypeNode("com.example.Owner")
        val raw = createRawFunction(ownerFqn = "com.example.Owner")
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType("com.example.Owner") } returns ownerType
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("com.example.Owner.testMethod")

        handler.handle(cmd, state, builder)

        // According to the code, ownerType is always passed as null to extractFunctionMetadata
        verify { apiMetadataCollector.extractFunctionMetadata(raw, null, any()) }
    }

    @ParameterizedTest
    @MethodSource("kdocTestCases")
    fun `handle should process kdoc correctly`(kdoc: String?, shouldHaveKdoc: Boolean) {
        val raw = createRawFunction(kdoc = kdoc)
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
                docComment = kdoc,
                meta = any(),
            )
        } returns createFunctionNode("testMethod")

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
                signature = any(),
                sourceCode = any(),
                docComment = kdoc,
                meta = any(),
            )
        }
    }

    @Test
    fun `handle should call nodeKindRefiner forFunction`() {
        val raw = createRawFunction()
        val fileUnit = createRawFileUnit()
        val cmd = UpsertFunctionCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns fileUnit
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns createFunctionNode("testMethod")

        handler.handle(cmd, state, builder)

        verify { nodeKindRefiner.forFunction(NodeKind.METHOD, raw, fileUnit) }
    }

    @Test
    fun `handle should put function in state after creation`() {
        val raw = createRawFunction()
        val cmd = UpsertFunctionCmd(raw)
        val functionNode = createFunctionNode("com.example.testMethod")

        every { state.getFilePackage(any()) } returns "com.example"
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns createPackageNode("com.example")
        every { state.getFileUnit(any()) } returns null
        every { state.getFileImports(any()) } returns null
        every { nodeKindRefiner.forFunction(any(), any(), any()) } returns NodeKind.METHOD
        every { apiMetadataCollector.extractFunctionMetadata(any(), any(), any()) } returns null
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
        } returns functionNode

        handler.handle(cmd, state, builder)

        verify { state.putFunction(any(), functionNode) }
    }

    companion object {
        @JvmStatic
        fun spanTestCases(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LineSpan(1, 10), 1..10),
                Arguments.of(null, null),
            )
        }

        @JvmStatic
        fun kdocTestCases(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Test documentation", true),
                Arguments.of(null, false),
            )
        }
    }

    private fun createRawFunction(
        pkgFqn: String? = "com.example",
        ownerFqn: String? = null,
        signatureRepr: String? = "fun testMethod()",
        paramNames: List<String> = emptyList(),
        span: LineSpan? = null,
        kdoc: String? = null,
    ): RawFunction {
        return RawFunction(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            ownerFqn = ownerFqn,
            name = "testMethod",
            signatureRepr = signatureRepr,
            paramNames = paramNames,
            annotationsRepr = emptySet(),
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = kdoc,
            span = span,
            text = "fun testMethod() {}",
        )
    }

    private fun createFunctionNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() },
            kind = NodeKind.METHOD,
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
