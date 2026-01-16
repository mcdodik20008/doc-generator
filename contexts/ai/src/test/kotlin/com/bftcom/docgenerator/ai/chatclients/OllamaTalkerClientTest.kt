package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
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
class OllamaTalkerClientTest {

    @Mock
    private lateinit var chatModel: ChatModel

    private lateinit var chatClient: ChatClient
    private lateinit var talkerClient: OllamaTalkerClient

    @BeforeEach
    fun setUp() {
        // Создаем реальный клиент. Ошибки Unresolved reference 'system' и 'user' исчезнут.
        chatClient = ChatClient.builder(chatModel).build()
        talkerClient = OllamaTalkerClient(chatClient)
    }

    @Test
    fun `rewrite should call chat client with system prompt and user request`() {
        // Arrange
        val request = TalkerRewriteRequest(
            nodeFqn = "com.example.Test",
            language = "ru",
            rawContent = "Technical description"
        )
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("Human readable description"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = talkerClient.rewrite(request)

        // Assert
        assertThat(result).isEqualTo("Human readable description")

        // Проверяем, что ушло в модель
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val userContent = promptCaptor.value.instructions.find { it.messageType.name == "USER" }?.text ?: ""
        assertThat(userContent).contains(request.nodeFqn)
        assertThat(userContent).contains(request.language)
        assertThat(userContent).contains("Technical description")
    }

    @Test
    fun `rewrite should remove redacted reasoning tags`() {
        // Arrange
        val request = TalkerRewriteRequest("fqn", "ru", "content")
        val responseWithReasoning = """
            <think>Some reasoning here</think>
            Human readable description
            <think>More reasoning</think>
        """.trimIndent()

        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage(responseWithReasoning))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = talkerClient.rewrite(request)

        // Assert
        assertThat(result).isEqualTo("Human readable description")
        assertThat(result).doesNotContain("<think>")
        assertThat(result).doesNotContain("Some reasoning here")
    }

    @Test
    fun `rewrite should trim whitespace from response`() {
        // Arrange
        val request = TalkerRewriteRequest("fqn", "ru", "content")
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("  Description  "))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = talkerClient.rewrite(request)

        // Assert
        assertThat(result).isEqualTo("Description")
    }

    @Test
    fun `rewrite should trim rawContent in user prompt`() {
        // Arrange
        val request = TalkerRewriteRequest("fqn", "ru", "  Technical description  ")
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("ok"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        talkerClient.rewrite(request)

        // Assert
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val userContent = promptCaptor.value.instructions.find { it.messageType.name == "USER" }?.text ?: ""
        assertThat(userContent).contains("Technical description")
        assertThat(userContent).doesNotContain("  Technical description  ")
    }
}