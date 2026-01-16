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
class OllamaCoderClientTest {

    @Mock
    private lateinit var chatModel: ChatModel

    private lateinit var chatClient: ChatClient
    private lateinit var coderClient: OllamaCoderClient

    @BeforeEach
    fun setUp() {
        // Создаем реальный клиент вокруг мока модели
        chatClient = ChatClient.builder(chatModel).build()
        coderClient = OllamaCoderClient(chatClient)
    }

    @Test
    fun `generate should call chat client with system prompt and context`() {
        // Arrange
        val systemPrompt = "You are a code explainer"
        val context = "public class Test {}"

        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("Explanation"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = coderClient.generate(context, systemPrompt)

        // Assert
        assertThat(result).isEqualTo("Explanation")

        // Проверяем содержимое промпта через Captor
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val prompt = promptCaptor.value
        val systemMessage = prompt.instructions.find { it.messageType.name == "SYSTEM" }
        val userMessage = prompt.instructions.find { it.messageType.name == "USER" }

        assertThat(systemMessage?.text).isEqualTo(systemPrompt)
        assertThat(userMessage?.text).isEqualTo(context)
    }

    @Test
    fun `generate should handle empty response`() {
        // Arrange
        // В Spring AI пустой ответ часто приходит как пустой список поколений или пустой контент
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage(""))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = coderClient.generate("Context", "System")

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `generate should trim whitespace from response`() {
        // Arrange
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("  Explanation  "))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = coderClient.generate("Context", "System")

        // Assert
        assertThat(result).isEqualTo("Explanation")
    }
}