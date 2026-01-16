package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnsurePackageHandlerTest {
    private val handler = EnsurePackageHandler()
    private val state = mockk<GraphState>(relaxed = true)
    private val builder = mockk<NodeBuilder>(relaxed = true)
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `handle should create package if not exists`() {
        val cmd = EnsurePackageCmd(
            pkgFqn = "com.example",
            filePath = "Test.kt",
            spanStart = 1,
            spanEnd = 10,
            sourceText = "package com.example",
        )

        val existingPackage = createPackageNode("com.example")
        every { state.getOrPutPackage(any(), any()) } returns existingPackage

        handler.handle(cmd, state, builder)

        verify { state.getOrPutPackage("com.example", any()) }
    }

    @Test
    fun `handle should use builder to create package when not exists`() {
        val cmd = EnsurePackageCmd(
            pkgFqn = "com.example",
            filePath = "Test.kt",
            spanStart = 1,
            spanEnd = 10,
            sourceText = "package com.example",
        )

        val newPackage = createPackageNode("com.example")
        every { state.getOrPutPackage(any(), any()) } answers {
            val factory = secondArg<() -> Node>()
            factory()
        }
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns newPackage

        handler.handle(cmd, state, builder)

        verify { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should extract package name correctly`() {
        val cmd = EnsurePackageCmd(
            pkgFqn = "com.example.subpackage",
            filePath = "Test.kt",
        )

        val newPackage = createPackageNode("com.example.subpackage")
        every { state.getOrPutPackage(any(), any()) } answers {
            val factory = secondArg<() -> Node>()
            factory()
        }
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns newPackage

        handler.handle(cmd, state, builder)

        verify {
            builder.upsertNode(
                fqn = "com.example.subpackage",
                kind = NodeKind.PACKAGE,
                name = "subpackage",
                packageName = "com.example.subpackage",
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
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
