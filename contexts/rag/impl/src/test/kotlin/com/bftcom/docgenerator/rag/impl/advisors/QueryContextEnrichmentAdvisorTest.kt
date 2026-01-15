package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType

class QueryContextEnrichmentAdvisorTest {

    @Test
    fun `process - ставит HAS_HISTORY=false если сообщений нет`() {
        val chatMemory = mockk<ChatMemory>()
        every { chatMemory.get(any()) } returns emptyList()
        val advisor = QueryContextEnrichmentAdvisor(chatMemory)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<Boolean>(QueryMetadataKeys.HAS_HISTORY)).isFalse
        assertThat(context.hasMetadata(QueryMetadataKeys.CONVERSATION_HISTORY)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }

    @Test
    fun `process - сохраняет историю и добавляет шаг`() {
        val chatMemory = mockk<ChatMemory>()
        val m1 = mockk<Message>()
        val m2 = mockk<Message>()
        every { m1.messageType } returns MessageType.USER
        every { m1.text } returns "Привет"
        every { m2.messageType } returns MessageType.ASSISTANT
        every { m2.text } returns "Чем помочь?"

        every { chatMemory.get(any()) } returns listOf(m1, m2)
        val advisor = QueryContextEnrichmentAdvisor(chatMemory)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "что такое RAG",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<Boolean>(QueryMetadataKeys.HAS_HISTORY)).isTrue
        val history = context.getMetadata<String>(QueryMetadataKeys.CONVERSATION_HISTORY)
        assertThat(history).isNotNull
        assertThat(history!!).contains("USER: Привет")
        assertThat(history).contains("ASSISTANT: Чем помочь?")
        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryContextEnrichment")
    }

    @Test
    fun `process - пишет HISTORY_ERROR если chatMemory падает`() {
        val chatMemory = mockk<ChatMemory>()
        every { chatMemory.get(any()) } throws RuntimeException("boom")
        val advisor = QueryContextEnrichmentAdvisor(chatMemory)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "q",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.getMetadata<String>(QueryMetadataKeys.HISTORY_ERROR)).isEqualTo("boom")
    }
}

