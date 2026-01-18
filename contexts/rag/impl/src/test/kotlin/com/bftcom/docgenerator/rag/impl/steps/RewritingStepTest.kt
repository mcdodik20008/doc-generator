package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class RewritingStepTest {
    private val chatClient = mockk<ChatClient>()
    private val step = RewritingStep(chatClient)

    @Test
    fun `execute - переформулирует запрос и переходит к EXPANSION`() {
        val original = "что делает process"
        val rewritten = "Что делает метод process?"

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns rewritten

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = original,
            currentQuery = original,
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).isEqualTo(rewritten)
        assertThat(result.context.getMetadata<Boolean>(QueryMetadataKeys.REWRITTEN)).isTrue
        assertThat(result.context.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)).isEqualTo(rewritten)
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - пропускает если уже переформулирован`() {
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.REWRITTEN, true)

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.processingSteps).isEmpty()
    }

    @Test
    fun `execute - обрабатывает ошибку LLM`() {
        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } throws RuntimeException("LLM error")
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).isEqualTo("test") // Не изменился
        assertThat(result.context.hasMetadata(QueryMetadataKeys.REWRITTEN)).isFalse
    }
}
