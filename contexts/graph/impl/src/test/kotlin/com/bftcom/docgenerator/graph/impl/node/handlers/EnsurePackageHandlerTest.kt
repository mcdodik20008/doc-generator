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
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnsurePackageHandlerTest {
    private val handler = EnsurePackageHandler()
    private val state = GraphState()
    private val builder = mockk<NodeBuilder>(relaxed = true)
    private val app = Application(id = 1L, key = "app", name = "App")

    private var nodeIdCounter = 1L

    @Test
    fun `handle should create full package hierarchy for multi-segment FQN`() {
        val cmd =
            EnsurePackageCmd(
                pkgFqn = "com.example.subpackage",
                filePath = "Test.kt",
                spanStart = 1,
                spanEnd = 10,
                sourceText = "package com.example.subpackage",
            )

        // Builder создаёт ноду и возвращает её
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        handler.handle(cmd, state, builder)

        // Проверяем, что создано 3 пакета: com, com.example, com.example.subpackage
        verify(exactly = 3) { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        // Проверяем каждый пакет
        verify {
            builder.upsertNode(
                fqn = "com",
                kind = NodeKind.PACKAGE,
                name = "com",
                any(),
                parent = null,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        verify {
            builder.upsertNode(
                fqn = "com.example",
                kind = NodeKind.PACKAGE,
                name = "example",
                any(),
                parent =
                    match {
                        it?.fqn == "com"
                    },
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        verify {
            builder.upsertNode(
                fqn = "com.example.subpackage",
                kind = NodeKind.PACKAGE,
                name = "subpackage",
                any(),
                parent =
                    match {
                        it?.fqn ==
                            "com.example"
                    },
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

    @Test
    fun `handle should set filePath and sourceText only on leaf package`() {
        val cmd =
            EnsurePackageCmd(
                pkgFqn = "com.example",
                filePath = "Test.kt",
                spanStart = 1,
                spanEnd = 10,
                sourceText = "package com.example",
            )

        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        handler.handle(cmd, state, builder)

        // Промежуточный "com" — без filePath и sourceCode
        verify {
            builder.upsertNode(
                fqn = "com",
                any(),
                any(),
                any(),
                any(),
                any(),
                filePath = null,
                span = null,
                any(),
                sourceCode = null,
                any(),
                any(),
            )
        }
        // Листовой "com.example" — с filePath и sourceCode
        verify {
            builder.upsertNode(
                fqn = "com.example",
                any(),
                any(),
                any(),
                any(),
                any(),
                filePath = "Test.kt",
                span = 1..10,
                any(),
                sourceCode = "package com.example",
                any(),
                any(),
            )
        }
    }

    @Test
    fun `handle should reuse existing packages from state`() {
        val cmd1 = EnsurePackageCmd(pkgFqn = "com.example.service", filePath = "Service.kt")
        val cmd2 = EnsurePackageCmd(pkgFqn = "com.example.config", filePath = "Config.kt")

        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        handler.handle(cmd1, state, builder)
        handler.handle(cmd2, state, builder)

        // "com" и "com.example" создаются только при первом вызове,
        // при втором — переиспользуются из state.
        // Итого: com, com.example, com.example.service (1-й вызов) + com.example.config (2-й) = 4
        verify(exactly = 4) { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle should work with single segment package`() {
        val cmd =
            EnsurePackageCmd(
                pkgFqn = "utils",
                filePath = "Utils.kt",
            )

        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        handler.handle(cmd, state, builder)

        verify(exactly = 1) {
            builder.upsertNode(
                fqn = "utils",
                kind = NodeKind.PACKAGE,
                name = "utils",
                any(),
                parent = null,
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

    @Test
    fun `handle should set parent correctly through deep hierarchy`() {
        val cmd =
            EnsurePackageCmd(
                pkgFqn = "com.bftcom.rr.uds.config.rabbit",
                filePath = "Rabbit.kt",
            )

        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        handler.handle(cmd, state, builder)

        // 6 пакетов создаётся
        verify(exactly = 6) { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        // Проверяем цепочку parent: com → bftcom → rr → uds → config → rabbit
        val comNode = state.getPackage("com")
        val bftcomNode = state.getPackage("com.bftcom")
        val rrNode = state.getPackage("com.bftcom.rr")
        val udsNode = state.getPackage("com.bftcom.rr.uds")
        val configNode = state.getPackage("com.bftcom.rr.uds.config")
        val rabbitNode = state.getPackage("com.bftcom.rr.uds.config.rabbit")

        assertThat(comNode).isNotNull
        assertThat(bftcomNode).isNotNull
        assertThat(rrNode).isNotNull
        assertThat(udsNode).isNotNull
        assertThat(configNode).isNotNull
        assertThat(rabbitNode).isNotNull

        assertThat(comNode!!.parent).isNull()
        assertThat(bftcomNode!!.parent?.fqn).isEqualTo("com")
        assertThat(rrNode!!.parent?.fqn).isEqualTo("com.bftcom")
        assertThat(udsNode!!.parent?.fqn).isEqualTo("com.bftcom.rr")
        assertThat(configNode!!.parent?.fqn).isEqualTo("com.bftcom.rr.uds")
        assertThat(rabbitNode!!.parent?.fqn).isEqualTo("com.bftcom.rr.uds.config")
    }

    @Test
    fun `ensurePackageChain should return leaf node`() {
        every { builder.upsertNode(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createPackageNode(firstArg(), parent = arg(4))
        }

        val result =
            ensurePackageChain(
                pkgFqn = "com.example.service",
                state = state,
                builder = builder,
                filePath = "Service.kt",
            )

        assertThat(result.fqn).isEqualTo("com.example.service")
        assertThat(result.kind).isEqualTo(NodeKind.PACKAGE)
    }

    private fun createPackageNode(
        pkgFqn: String,
        parent: Node? = null,
    ): Node =
        Node(
            id = nodeIdCounter++,
            application = app,
            fqn = pkgFqn,
            name = pkgFqn.substringAfterLast('.'),
            packageName = pkgFqn,
            kind = NodeKind.PACKAGE,
            lang = Lang.kotlin,
            parent = parent,
        )
}
