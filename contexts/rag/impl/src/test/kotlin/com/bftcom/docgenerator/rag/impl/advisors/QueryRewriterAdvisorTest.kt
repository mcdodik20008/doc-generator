package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class QueryRewriterAdvisorTest {

    @Test
    fun `process - пропускает если уже был REWRITTEN`() {
        val chatClient = mockk<ChatClient>(relaxed = true)
        val advisor = QueryRewriterAdvisor(chatClient)
        var context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.REWRITTEN, true)

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { chatClient.prompt() }
        assertThat(context.processingSteps).isEmpty()
    }

    @Test
    fun `process - обновляет запрос и метаданные когда LLM вернул другую формулировку`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryRewriterAdvisor(chatClient)
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

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.currentQuery).isEqualTo(rewritten)
        assertThat(context.getMetadata<Boolean>(QueryMetadataKeys.REWRITTEN)).isTrue
        assertThat(context.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)).isEqualTo(rewritten)
        assertThat(context.getMetadata<String>(QueryMetadataKeys.PREVIOUS_QUERY)).isEqualTo(original)
        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryRewriter")
    }

    @Test
    fun `process - не меняет контекст если LLM вернул null`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryRewriterAdvisor(chatClient)
        val original = "q"

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns null

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = original,
            currentQuery = original,
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.currentQuery).isEqualTo(original)
        assertThat(context.hasMetadata(QueryMetadataKeys.REWRITTEN)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }
}

