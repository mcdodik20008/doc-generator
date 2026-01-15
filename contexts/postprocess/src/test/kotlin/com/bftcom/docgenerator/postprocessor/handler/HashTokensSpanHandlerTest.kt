package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HashTokensSpanHandlerTest {
    private val h = HashTokensSpanHandler()

    @Test
    fun `produce - вычисляет hash tokenCount spanChars`() {
        val snap = snap("hello world")
        val mut = h.produce(snap)

        assertThat(mut.provided[FieldKey.CONTENT_HASH] as String).hasSize(64)
        assertThat(mut.provided[FieldKey.TOKEN_COUNT]).isEqualTo(2)
        assertThat(mut.provided[FieldKey.SPAN_CHARS]).isEqualTo("[0,11)")
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

