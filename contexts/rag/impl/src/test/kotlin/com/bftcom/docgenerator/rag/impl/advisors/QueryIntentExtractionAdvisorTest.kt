package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class QueryIntentExtractionAdvisorTest {

    @Test
    fun `process - пропускает если INTENT уже есть`() {
        val chatClient = mockk<ChatClient>(relaxed = true)
        val advisor = QueryIntentExtractionAdvisor(chatClient)
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).apply {
            setMetadata(QueryMetadataKeys.INTENT, "CODE_SEARCH")
        }

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { chatClient.prompt() }
        assertThat(context.processingSteps).isEmpty()
    }

    @Test
    fun `process - сохраняет uppercased intent и шаг`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryIntentExtractionAdvisor(chatClient)

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns "usage_example"

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "покажи пример использования",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<String>(QueryMetadataKeys.INTENT)).isEqualTo("USAGE_EXAMPLE")
        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryIntentExtraction")
    }

    @Test
    fun `process - ставит OTHER если LLM вернул null`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryIntentExtractionAdvisor(chatClient)

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns null

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<String>(QueryMetadataKeys.INTENT)).isEqualTo("OTHER")
    }
}

