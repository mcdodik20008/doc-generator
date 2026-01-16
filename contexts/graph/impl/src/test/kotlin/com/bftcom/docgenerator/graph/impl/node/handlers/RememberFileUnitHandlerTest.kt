package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class RememberFileUnitHandlerTest {
    private val handler = RememberFileUnitHandler()
    private val state = mockk<GraphState>(relaxed = true)
    private val builder = mockk<NodeBuilder>(relaxed = true)

    @Test
    fun `handle should call state rememberFileUnit`() {
        val fileUnit = createRawFileUnit()
        val cmd = RememberFileUnitCmd(fileUnit)

        handler.handle(cmd, state, builder)

        verify { state.rememberFileUnit(fileUnit) }
    }

    @Test
    fun `handle should not throw exception`() {
        val fileUnit = createRawFileUnit()
        val cmd = RememberFileUnitCmd(fileUnit)

        assertThatCode {
            handler.handle(cmd, state, builder)
        }.doesNotThrowAnyException()
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
