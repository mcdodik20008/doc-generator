package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExplainHandlerTest {
    private val h = ExplainHandler()

    @Test
    fun `produce - формирует explain_md и explain_quality_json`() {
        val snap = snap("hello world")
        val mut = h.produce(snap)

        val md = mut.provided[FieldKey.EXPLAIN_MD] as String
        val q = mut.provided[FieldKey.EXPLAIN_QUALITY_JSON] as String

        assertThat(md).contains("### Summary")
        assertThat(md).contains("#### Preview")
        assertThat(q).contains("\"grade\"")
        assertThat(q).contains("\"tokens\":2")
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

