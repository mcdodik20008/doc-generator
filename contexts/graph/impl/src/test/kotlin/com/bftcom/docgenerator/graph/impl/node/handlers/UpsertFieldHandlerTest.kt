package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UpsertFieldHandlerTest {
    private val nodeKindRefiner = mockk<NodeKindRefiner>(relaxed = true)
    private val handler = UpsertFieldHandler(nodeKindRefiner)
    private val state = mockk<GraphState>(relaxed = true)
    private val builder = mockk<NodeBuilder>(relaxed = true)
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `handle should use pkgFqn from raw when available`() {
        val raw = createRawField(pkgFqn = "com.example", ownerFqn = null)
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { builder.upsertNode(any(), any(), any(), packageName = "com.example", any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should use file package when pkgFqn is null`() {
        val raw = createRawField(pkgFqn = null, ownerFqn = null)
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns "com.example"
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { state.getFilePackage(raw.filePath) }
        verify { builder.upsertNode(any(), any(), any(), packageName = "com.example", any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should use owner type as parent when ownerFqn is not null`() {
        val ownerType = createTypeNode("com.example.Owner")
        val raw = createRawField(pkgFqn = "com.example", ownerFqn = "com.example.Owner")
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType("com.example.Owner") } returns ownerType
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { state.getType("com.example.Owner") }
        verify { builder.upsertNode(any(), any(), any(), any(), parent = ownerType, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should use package as parent when ownerFqn is null`() {
        val pkg = createPackageNode("com.example")
        val raw = createRawField(pkgFqn = "com.example", ownerFqn = null)
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage("com.example") } returns pkg
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { state.getPackage("com.example") }
        verify { builder.upsertNode(any(), any(), any(), any(), parent = pkg, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should use null parent when both ownerFqn and pkgFqn are null`() {
        val raw = createRawField(pkgFqn = null, ownerFqn = null)
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { builder.upsertNode(any(), any(), any(), any(), parent = null, any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should call nodeKindRefiner forField`() {
        val raw = createRawField()
        val cmd = UpsertFieldCmd(raw)

        every { state.getFilePackage(any()) } returns null
        every { state.getType(any()) } returns null
        every { state.getPackage(any()) } returns null
        every { state.getFileUnit(any()) } returns null
        every { nodeKindRefiner.forField(any(), any(), any()) } returns NodeKind.FIELD
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns createFieldNode()

        handler.handle(cmd, state, builder)

        verify { nodeKindRefiner.forField(NodeKind.FIELD, raw, null) }
    }

    private fun createRawField(
        pkgFqn: String? = "com.example",
        ownerFqn: String? = null,
    ): RawField {
        return RawField(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            ownerFqn = ownerFqn,
            name = "fieldName",
            typeRepr = "String",
            annotationsRepr = emptyList(),
            kdoc = null,
            span = null,
            text = null,
        )
    }

    private fun createFieldNode(): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = "com.example.fieldName",
            name = "fieldName",
            packageName = "com.example",
            kind = NodeKind.FIELD,
            lang = Lang.kotlin,
        )
    }

    private fun createTypeNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
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
        )
    }
}
