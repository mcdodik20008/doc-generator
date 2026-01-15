package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownFlagsHandlerTest {
    private val h = MarkdownFlagsHandler()

    @Test
    fun `produce - выставляет md флаги`() {
        val snap = snap("# Title")
        val mut = h.produce(snap)
        assertThat(mut.provided[FieldKey.USES_MD]).isEqualTo("md")
        assertThat(mut.provided[FieldKey.USED_BY_MD]).isEqualTo("md")
    }

    @Test
    fun `produce - выставляет null если markdown не найден`() {
        val snap = snap("plain text")
        val mut = h.produce(snap)
        assertThat(mut.provided[FieldKey.USES_MD]).isNull()
        assertThat(mut.provided[FieldKey.USED_BY_MD]).isNull()
    }

    private fun snap(content: String) =
        ChunkSnapshot(
            id = 1L,
            content = content,
            contentHash = null,
            tokenCount = null,
            spanChars = null,
            usesMd = null,
            usedByMd = null,
            explainMd = null,
            explainQualityJson = null,
            embPresent = false,
            embedModel = null,
            embedTs = null,
        )
}

