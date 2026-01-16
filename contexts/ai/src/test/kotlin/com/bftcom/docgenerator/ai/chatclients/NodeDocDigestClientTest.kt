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
class NodeDocDigestClientTest {

    @Mock
    private lateinit var chatModel: ChatModel

    private lateinit var chatClient: ChatClient
    private lateinit var digestClient: NodeDocDigestClient

    @BeforeEach
    fun setUp() {
        // Создаем реальный клиент вокруг мока модели.
        // Это автоматически чинит все ошибки с PromptUserSpec.
        chatClient = ChatClient.builder(chatModel).build()
        digestClient = NodeDocDigestClient(chatClient)
    }

    @Test
    fun `generate should call chat client with correct parameters`() {
        // Arrange
        val kind = "CLASS"
        val fqn = "com.example.Test"
        val docTech = "Technical documentation"
        val deps = listOf("com.example.Dep1", "com.example.Dep2")

        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("kind=CLASS\nfqn=com.example.Test"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        val result = digestClient.generate(kind, fqn, docTech, deps)

        // Assert
        assertThat(result).contains("kind=CLASS")

        // Проверяем, что в модель ушел правильный промпт
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val fullPrompt = promptCaptor.value.contents
        assertThat(fullPrompt).contains("NODE_KIND=$kind")
        assertThat(fullPrompt).contains("FQN=$fqn")
        assertThat(fullPrompt).contains("Technical documentation")
    }

    @Test
    fun `generate should handle empty dependencies`() {
        // Arrange
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("kind=CLASS"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Actcontexts-chunking-impl
        digestClient.generate("CLASS", "com.example.Test", "Tech", emptyList())

        // Assert
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())
        assertThat(promptCaptor.value.contents).contains("DEPS=none")
    }

    @Test
    fun `generate should truncate long dependencies list`() {
        // Arrange
        val deps = (1..100).map { "com.example.Dep$it" }
        val aiResponse = ChatResponse(listOf(Generation(AssistantMessage("ok"))))
        whenever(chatModel.call(any<Prompt>())).thenReturn(aiResponse)

        // Act
        digestClient.generate("CLASS", "fqn", "tech", deps)

        // Assert
        val promptCaptor = ArgumentCaptor.forClass(Prompt::class.java)
        verify(chatModel).call(promptCaptor.capture())

        val userContent = promptCaptor.value.instructions.find { it.messageType.name == "USER" }?.text ?: ""
        val depsLine = userContent.lines().find { it.startsWith("DEPS=") } ?: ""
        assertThat(depsLine.length).isLessThanOrEqualTo(800 + 5)
    }
}