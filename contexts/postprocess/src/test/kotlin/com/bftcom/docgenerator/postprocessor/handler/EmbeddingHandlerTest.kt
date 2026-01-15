package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmbeddingHandlerTest {
    @Test
    fun `supports - выключено или embPresent или client null`() {
        val snapNoEmb = snap(embPresent = false)
        val snapWithEmb = snap(embPresent = true)

        val client = mockk<EmbeddingClient>(relaxed = true)

        assertThat(EmbeddingHandler(client = null, enabled = true).supports(snapNoEmb)).isFalse()
        assertThat(EmbeddingHandler(client = client, enabled = false).supports(snapNoEmb)).isFalse()
        assertThat(EmbeddingHandler(client = client, enabled = true).supports(snapWithEmb)).isFalse()
    }

    @Test
    fun `produce - кладет emb model ts`() {
        val client = mockk<EmbeddingClient>()
        every { client.modelName } returns "m1"
        every { client.dim } returns 2
        every { client.embed(any()) } returns floatArrayOf(0.1f, 0.2f)

        val h = EmbeddingHandler(client = client, enabled = true)
        val mut = h.produce(snap(embPresent = false))

        assertThat(mut.provided[FieldKey.EMB]).isInstanceOf(FloatArray::class.java)
        assertThat(mut.provided[FieldKey.EMBED_MODEL]).isEqualTo("m1")
        assertThat(mut.provided[FieldKey.EMBED_TS]).isNotNull
    }

    private fun snap(embPresent: Boolean) =
        ChunkSnapshot(
            id = 1L,
            content = "hello",
            contentHash = null,
            tokenCount = null,
            spanChars = null,
            usesMd = null,
            usedByMd = null,
            explainMd = null,
            explainQualityJson = null,
            embPresent = embPresent,
            embedModel = null,
            embedTs = null,
        )
}

