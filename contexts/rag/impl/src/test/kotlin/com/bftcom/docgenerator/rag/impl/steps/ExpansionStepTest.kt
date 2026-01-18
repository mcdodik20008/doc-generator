package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class ExpansionStepTest {
    private val chatClient = mockk<ChatClient>()
    private val step = ExpansionStep(chatClient)

    @Test
    fun `execute - генерирует расширенные запросы и переходит к VECTOR_SEARCH`() {
        val currentQuery = "Как работает авторизация?"
        val expansionResponse = """
            Как устроена авторизация пользователей?
            Проверка прав доступа в системе
            Механизм аутентификации
        """.trimIndent()

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns expansionResponse

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = currentQuery,
            currentQuery = currentQuery,
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        val expandedQueries = result.context.getMetadata<List<String>>(QueryMetadataKeys.EXPANDED_QUERIES)
        assertThat(expandedQueries).isNotNull
        assertThat(expandedQueries!!).hasSize(3)
        assertThat(expandedQueries).doesNotContain(currentQuery)
        assertThat(result.context.processingSteps).hasSize(1)
    }

    @Test
    fun `execute - пропускает если уже расширен`() {
        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.EXPANDED, true)

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.processingSteps).isEmpty()
    }

    @Test
    fun `execute - обрабатывает пустой ответ LLM`() {
        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns null

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "test",
            currentQuery = "test",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.EXPANDED_QUERIES)).isFalse
    }

    @Test
    fun `execute - фильтрует пустые строки и текущий запрос`() {
        val currentQuery = "Как работает авторизация?"
        val expansionResponse = """
            $currentQuery
            
            Как устроена авторизация?
            
            Проверка прав доступа
        """.trimIndent()

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns expansionResponse

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = currentQuery,
            currentQuery = currentQuery,
            sessionId = "s-1",
        )

        val result = step.execute(context)

        val expandedQueries = result.context.getMetadata<List<String>>(QueryMetadataKeys.EXPANDED_QUERIES)
        assertThat(expandedQueries).isNotNull
        assertThat(expandedQueries!!).doesNotContain(currentQuery)
        assertThat(expandedQueries).hasSize(2) // Только "Как устроена авторизация?" и "Проверка прав доступа"
    }
}
