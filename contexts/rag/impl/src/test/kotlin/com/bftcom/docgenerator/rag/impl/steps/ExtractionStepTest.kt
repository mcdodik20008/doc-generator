package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class ExtractionStepTest {
    private val chatClient = mockk<ChatClient>()
    private val objectMapper = ObjectMapper()
    private val step = ExtractionStep(chatClient, objectMapper)

    @Test
    fun `execute - извлекает класс и метод через LLM и переходит к EXACT_SEARCH`() {
        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns """{"className":"UserService","methodName":"getUser"}"""

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "что делает getUser в UserService",
            currentQuery = "что делает getUser в UserService",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("FOUND")
        val extractionResult = result.context.getMetadata<Map<*, *>>(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)
        assertThat(extractionResult).isNotNull
        assertThat(extractionResult!!["className"]).isEqualTo("UserService")
        assertThat(extractionResult["methodName"]).isEqualTo("getUser")
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - переходит к REWRITING если не удалось извлечь`() {
        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns null

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "как работает авторизация",
            currentQuery = "как работает авторизация",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("NOT_FOUND")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)).isFalse
    }

    @Test
    fun `execute - обрабатывает некорректный JSON ответ`() {
        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns "некорректный JSON"

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "test query",
            currentQuery = "test query",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        // Должен перейти к REWRITING при ошибке парсинга
        assertThat(result.transitionKey).isEqualTo("NOT_FOUND")
    }
}
