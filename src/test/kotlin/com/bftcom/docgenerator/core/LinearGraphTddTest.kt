package com.bftcom.docgenerator.core

import com.bftcom.docgenerator.core.api.ContextBuilder
import com.bftcom.docgenerator.core.api.GraphPort
import com.bftcom.docgenerator.core.api.LlmClient
import com.bftcom.docgenerator.core.api.NodeDocPort
import com.bftcom.docgenerator.core.model.DepLine
import com.bftcom.docgenerator.core.model.NodeDocDraft
import com.bftcom.docgenerator.core.model.NodeDocPatch
import com.bftcom.docgenerator.core.model.UsageLine
import com.bftcom.docgenerator.passes.BottomUpPass
import com.bftcom.docgenerator.passes.TopDownPass
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LinearGraphTddTest {

    private val graph = mockk<GraphPort>()
    private val ctx = mockk<ContextBuilder>()
    private val llm = mockk<LlmClient>()
    private val docs = mockk<NodeDocPort>()

    private val N1 = 1L; private val N2 = 2L; private val N3 = 3L; private val N4 = 4L

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { graph.topoOrder() } returns listOf(N4, N3, N2, N1)
        every { graph.reverseTopoOrder() } returns listOf(N1, N2, N3, N4)

        every { graph.dependenciesOf(N1) } returns listOf(N2)
        every { graph.dependenciesOf(N2) } returns listOf(N3)
        every { graph.dependenciesOf(N3) } returns listOf(N4)
        every { graph.dependenciesOf(N4) } returns emptyList()

        every { graph.dependentsOf(N4) } returns listOf(N3)
        every { graph.dependentsOf(N3) } returns listOf(N2)
        every { graph.dependentsOf(N2) } returns listOf(N1)
        every { graph.dependentsOf(N1) } returns emptyList()

        every { ctx.depsContext(listOf(N2), any()) } returns listOf(DepLine(N2, "N2: service"))
        every { ctx.depsContext(listOf(N3), any()) } returns listOf(DepLine(N3, "N3: repo"))
        every { ctx.depsContext(listOf(N4), any()) } returns listOf(DepLine(N4, "N4: db"))
        every { ctx.depsContext(emptyList(), any()) } returns emptyList()

        every { ctx.usageContext(listOf(N3), any()) } returns listOf(UsageLine(N3, "N3 uses N4"))
        every { ctx.usageContext(listOf(N2), any()) } returns listOf(UsageLine(N2, "N2 uses N3"))
        every { ctx.usageContext(listOf(N1), any()) } returns listOf(UsageLine(N1, "N1 uses N2"))
        every { ctx.usageContext(emptyList(), any()) } returns emptyList()

        every { llm.generateNodeDoc(N4, any(), any()) } returns NodeDocDraft(summary = "N4 sum", details = "N4 det")
        every { llm.generateNodeDoc(N3, any(), any()) } returns NodeDocDraft(summary = "N3 sum", details = "N3 uses N4")
        every { llm.generateNodeDoc(N2, any(), any()) } returns NodeDocDraft(summary = "N2 sum", details = "N2 uses N3")
        every { llm.generateNodeDoc(N1, any(), any()) } returns NodeDocDraft(summary = "N1 sum", details = "N1 uses N2")

        every { llm.generateUsagePatch(N4, any(), any()) } returns NodeDocPatch(usedBy = listOf("N3 depends on N4"))
        every { llm.generateUsagePatch(N3, any(), any()) } returns NodeDocPatch(usedBy = listOf("N2 depends on N3"))
        every { llm.generateUsagePatch(N2, any(), any()) } returns NodeDocPatch(usedBy = listOf("N1 depends on N2"))
        every { llm.generateUsagePatch(N1, any(), any()) } returns NodeDocPatch(usedBy = emptyList())

        // NodeDocPort — просто подтверждаем вызовы (возврат можно стаббить произвольным объектом)
        every { docs.upsert(any(), any(), any(), any()) } answers {
            // быстрая sanity-проверка содержания
            val draft = secondArg<NodeDocDraft>()
            assertThat(draft.summary).isNotBlank
            mockk(relaxed = true) // NodeDoc
        }
        every { docs.merge(any(), any(), any(), any()) } answers {
            val patch = secondArg<NodeDocPatch>()
            assertThat(patch.usedBy).isNotNull
            mockk(relaxed = true)
        }
    }

    @Test
    fun `linear graph bottom-up then top-down produces 4 upserts and 4 merges in correct order`() {
        val bottomUp = BottomUpPass(graph, ctx, llm, docs, locale = "ru", modelName = "unit-llm")
        val topDown = TopDownPass(graph, ctx, llm, docs, locale = "ru", modelName = "unit-llm")

        bottomUp.run()
        topDown.run()

        // Порядок bottom-up: 4 -> 3 -> 2 -> 1
        verifyOrder {
            graph.topoOrder()

            graph.dependenciesOf(N4)
            ctx.depsContext(emptyList(), "ru")
            llm.generateNodeDoc(N4, any(), "ru")
            docs.upsert(N4, any(), "llm-bottom-up", "unit-llm")

            graph.dependenciesOf(N3)
            ctx.depsContext(listOf(N4), "ru")
            llm.generateNodeDoc(N3, any(), "ru")
            docs.upsert(N3, any(), "llm-bottom-up", "unit-llm")

            graph.dependenciesOf(N2)
            ctx.depsContext(listOf(N3), "ru")
            llm.generateNodeDoc(N2, any(), "ru")
            docs.upsert(N2, any(), "llm-bottom-up", "unit-llm")

            graph.dependenciesOf(N1)
            ctx.depsContext(listOf(N2), "ru")
            llm.generateNodeDoc(N1, any(), "ru")
            docs.upsert(N1, any(), "llm-bottom-up", "unit-llm")
        }

        // Порядок top-down: 1 -> 2 -> 3 -> 4
        verifyOrder {
            graph.reverseTopoOrder()

            graph.dependentsOf(N1)
            ctx.usageContext(emptyList(), "ru")
            llm.generateUsagePatch(N1, any(), "ru")
            docs.merge(N1, any(), "llm-top-down", "unit-llm")

            graph.dependentsOf(N2)
            ctx.usageContext(listOf(N1), "ru")
            llm.generateUsagePatch(N2, any(), "ru")
            docs.merge(N2, any(), "llm-top-down", "unit-llm")

            graph.dependentsOf(N3)
            ctx.usageContext(listOf(N2), "ru")
            llm.generateUsagePatch(N3, any(), "ru")
            docs.merge(N3, any(), "llm-top-down", "unit-llm")

            graph.dependentsOf(N4)
            ctx.usageContext(listOf(N3), "ru")
            llm.generateUsagePatch(N4, any(), "ru")
            docs.merge(N4, any(), "llm-top-down", "unit-llm")
        }

        verify(exactly = 4) { docs.upsert(any(), any(), "llm-bottom-up", "unit-llm") }
        verify(exactly = 4) { docs.merge(any(), any(), "llm-top-down", "unit-llm") }
        confirmVerified(graph, ctx, llm, docs)
    }
}