package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class QueryExpansionAdvisorTest {

    @Test
    fun `process - пропускает если EXPANDED уже выставлен`() {
        val chatClient = mockk<ChatClient>(relaxed = true)
        val advisor = QueryExpansionAdvisor(chatClient)
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        ).apply {
            setMetadata(QueryMetadataKeys.EXPANDED, true)
        }

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { chatClient.prompt() }
        assertThat(context.hasMetadata(QueryMetadataKeys.EXPANDED_QUERIES)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }

    @Test
    fun `process - парсит строки, фильтрует пустые и текущий запрос, ограничивает 3`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryExpansionAdvisor(chatClient)
        val currentQuery = "Как работает авторизация?"

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns """
            Как устроена авторизация пользователей?
            
            $currentQuery
            Проверка прав доступа в системе
            Ещё одна лишняя строка
        """.trimIndent()

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = currentQuery,
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<Boolean>(QueryMetadataKeys.EXPANDED)).isTrue

        val expanded = context.getMetadata<List<String>>(QueryMetadataKeys.EXPANDED_QUERIES)
        assertThat(expanded).isNotNull
        assertThat(expanded!!).doesNotContain(currentQuery)
        assertThat(expanded).hasSize(3)

        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryExpansion")
    }

    @Test
    fun `process - не пишет метаданные если LLM вернул пусто`() {
        val chatClient = mockk<ChatClient>()
        val advisor = QueryExpansionAdvisor(chatClient)

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
        assertThat(context.hasMetadata(QueryMetadataKeys.EXPANDED)).isFalse
        assertThat(context.hasMetadata(QueryMetadataKeys.EXPANDED_QUERIES)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }
}

