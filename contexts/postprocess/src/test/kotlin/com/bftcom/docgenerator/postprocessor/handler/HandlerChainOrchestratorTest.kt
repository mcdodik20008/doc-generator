package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class HandlerChainOrchestratorTest {
    @Test
    fun `processOne - мерджит патчи и обновляет meta и emb`() {
        val repo = mockk<ChunkRepository>(relaxed = true)

        val h1 = mockk<PostprocessHandler>()
        every { h1.supports(any()) } returns true
        every { h1.produce(any()) } returns
            PartialMutation()
                .set(FieldKey.EXPLAIN_MD, "long explanation")
                .set(FieldKey.EXPLAIN_QUALITY_JSON, """{"grade":"B"}""")

        val ts = OffsetDateTime.parse("2025-01-01T00:00:00Z")
        val emb = floatArrayOf(1.0f, 2.0f)

        val h2 = mockk<PostprocessHandler>()
        every { h2.supports(any()) } returns true
        every { h2.produce(any()) } returns
            PartialMutation()
                .set(FieldKey.EMB, emb)
                .set(FieldKey.EMBED_MODEL, "m1")
                .set(FieldKey.EMBED_TS, ts)
                .set(FieldKey.EXPLAIN_QUALITY_JSON, """{"grade":"A"}""") // лучше чем B

        // handler который падает — должен быть проглочен
        val bad = mockk<PostprocessHandler>()
        every { bad.supports(any()) } returns true
        every { bad.produce(any()) } throws RuntimeException("boom")

        val orch = HandlerChainOrchestrator(repo, listOf(h1, h2, bad))

        val chunk = sampleChunk(
            id = 1L,
            content = "hello world",
            contentHash = null,
            tokenCount = null,
            spanChars = null,
            usesMd = null,
            usedByMd = null,
            explainMd = null,
            explainQuality = emptyMap(),
            emb = null,
        )

        orch.processOne(chunk)

        verify(exactly = 1) {
            repo.updateEmb(1L, "[1.0,2.0]")
        }
        verify(atLeast = 2) {
            repo.updatePostMeta(
                id = 1L,
                contentHash = any(),
                tokenCount = any(),
                spanChars = any(),
                usesMd = any(),
                usedByMd = any(),
                embedModel = any(),
                embedTs = any(),
                explainMd = any(),
                explainQualityJson = any(),
            )
        }
    }

    @Test
    fun `processOne - пробрасывает исключение updatePostMeta`() {
        val repo = mockk<ChunkRepository>()
        every { repo.updatePostMeta(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("db down")

        val h = mockk<PostprocessHandler>()
        every { h.supports(any()) } returns true
        every { h.produce(any()) } returns PartialMutation().set(FieldKey.EXPLAIN_MD, "x")

        val orch = HandlerChainOrchestrator(repo, listOf(h))
        val chunk = sampleChunk(id = 1L, content = "x")

        val ex = org.junit.jupiter.api.assertThrows<RuntimeException> { orch.processOne(chunk) }
        assertThat(ex.message).contains("db down")
    }

    private fun sampleChunk(
        id: Long,
        content: String,
        contentHash: String? = null,
        tokenCount: Int? = null,
        spanChars: String? = null,
        usesMd: String? = null,
        usedByMd: String? = null,
        explainMd: String? = null,
        explainQuality: Map<String, Any> = emptyMap(),
        emb: FloatArray? = null,
    ): Chunk {
        val app = Application(key = "app", name = "App")
        val node =
            Node(
                id = 10L,
                application = app,
                fqn = "com.example.Foo",
                name = "Foo",
                kind = NodeKind.CLASS,
                lang = Lang.kotlin,
            )
        return Chunk(
            id = id,
            application = app,
            node = node,
            source = "code",
            content = content,
            title = "t",
            contentHash = contentHash,
            tokenCount = tokenCount,
            spanChars = spanChars,
            usesMd = usesMd,
            usedByMd = usedByMd,
            explainMd = explainMd,
            explainQuality = explainQuality,
            emb = emb,
        )
    }
}

