package com.bftcom.docgenerator.ai.chatclients

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

@ExtendWith(MockitoExtension::class)
class SummaryClientTest {

    @Mock
    private lateinit var chatModel: ChatModel

    private lateinit var chatClient: ChatClient
    private lateinit var summaryClient: SummaryClient

    @BeforeEach
    fun setUp() {
        // Создаем реальный ChatClient. Теперь методы .system() и .user() будут работать штатно.
        chatClient = ChatClient.builder(chatModel).build()
        summaryClient = SummaryClient(chatClient)
    }

    @Test
    fun `summarize should call chat client with system prompt and user text`() {
        // Arrange
        val inputText = "This is a very long text that needs to be summarized."
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("Summarized text"))))

        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = summaryClient.summarize(inputText)

        // Assert
        assertThat(result).isEqualTo("Summarized text")

        // Проверяем через Captor, что промпты дошли до модели
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val instructions = promptCaptor.value.instructions
        assertThat(instructions.any { it.messageType.name == "SYSTEM" }).isTrue()
        assertThat(instructions.any { it.text.contains(inputText) }).isTrue()
    }

    @Test
    fun `summarize should handle empty response`() {
        // Arrange
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage(""))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = summaryClient.summarize("Some text")

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `summarize should trim whitespace from response`() {
        // Arrange
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("  Summarized text  "))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = summaryClient.summarize("Some text")

        // Assert
        assertThat(result).isEqualTo("Summarized text")
    }

    @Test
    fun `summarize should include input text in user prompt`() {
        // Arrange
        val inputText = "Very important content here"
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("Summary"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        summaryClient.summarize(inputText)

        // Assert
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val userContent = promptCaptor.value.instructions
            .find { it.messageType.name == "USER" }?.text ?: ""
        assertThat(userContent).contains(inputText)
    }
}