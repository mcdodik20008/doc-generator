package com.bftcom.docgenerator.ai.advisor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.prompt.ChatOptions
@ExtendWith(MockitoExtension::class)
class ChatClientLoggingAdvisorTest {
    @Mock
    private lateinit var chatClientRequest: ChatClientRequest

    @Mock
    private lateinit var chatClientResponse: ChatClientResponse

    @Mock
    private lateinit var advisorChain: AdvisorChain

    @Mock
    private lateinit var prompt: Prompt

    @Mock
    private lateinit var chatResponse: ChatResponse

    @Mock
    private lateinit var metadata: ChatResponseMetadata

    @Mock
    private lateinit var usage: Usage

    private lateinit var advisor: ChatClientLoggingAdvisor

    @BeforeEach
    fun setUp() {
        advisor = ChatClientLoggingAdvisor()
    }

    @Test
    fun `getOrder should return 0`() {
        assertThat(advisor.getOrder()).isEqualTo(0)
    }

    @Test
    fun `before should return same request without modification`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.contents).thenReturn("Test prompt")
        whenever(prompt.options).thenReturn(ChatOptions.builder().build())

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `before should handle exception when extracting prompt contents`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.contents).thenThrow(RuntimeException("Test exception"))

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `before should use unknown model when model extraction fails`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.contents).thenReturn("Test prompt")
        whenever(prompt.options).thenThrow(RuntimeException("Test exception"))

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `after should return same response without modification`() {
        whenever(chatClientResponse.chatResponse()).thenReturn(chatResponse)
        whenever(chatResponse.metadata).thenReturn(metadata)
        whenever(chatResponse.result).thenReturn(Generation(AssistantMessage(("Test response"))))
        whenever(metadata.model).thenReturn("test-model")
        whenever(metadata.usage).thenReturn(usage)
        whenever(usage.promptTokens).thenReturn(10)
        whenever(usage.completionTokens).thenReturn(20)
        whenever(usage.totalTokens).thenReturn(30)

        val result = advisor.after(chatClientResponse, advisorChain)

        assertThat(result).isSameAs(chatClientResponse)
    }

    @Test
    fun `after should handle exception when extracting metadata`() {
        whenever(chatClientResponse.chatResponse()).thenReturn(chatResponse)
        whenever(chatResponse.metadata).thenThrow(RuntimeException("Test exception"))

        val result = advisor.after(chatClientResponse, advisorChain)

        assertThat(result).isSameAs(chatClientResponse)
    }

    @Test
    fun `after should handle exception when extracting response content`() {
        whenever(chatClientResponse.chatResponse()).thenReturn(chatResponse)
        whenever(chatResponse.metadata).thenReturn(metadata)
        whenever(chatResponse.result).thenThrow(RuntimeException("Test exception"))

        val result = advisor.after(chatClientResponse, advisorChain)

        assertThat(result).isSameAs(chatClientResponse)
    }

    @Test
    fun `after should handle null chatResponse`() {
        whenever(chatClientResponse.chatResponse()).thenReturn(null)

        val result = advisor.after(chatClientResponse, advisorChain)

        assertThat(result).isSameAs(chatClientResponse)
    }

    @Test
    fun `after should handle null duration when start time is not set`() {
        // Create new advisor instance to ensure clean state
        val newAdvisor = ChatClientLoggingAdvisor()
        whenever(chatClientResponse.chatResponse()).thenReturn(chatResponse)
        whenever(chatResponse.metadata).thenReturn(metadata)
        whenever(chatResponse.result).thenReturn(Generation(AssistantMessage("Test")))
        whenever(metadata.model).thenReturn("test-model")
        whenever(metadata.usage).thenReturn(usage)

        val result = newAdvisor.after(chatClientResponse, advisorChain)

        assertThat(result).isSameAs(chatClientResponse)
    }

    @Test
    fun `shortenForLog should return empty for blank text`() {
        val method = ChatClientLoggingAdvisor::class.java.getDeclaredMethod("shortenForLog", String::class.java)
        method.isAccessible = true

        val result = method.invoke(advisor, "   ") as String

        assertThat(result).isEqualTo("<empty>")
    }

    @Test
    fun `shortenForLog should return full text if shorter than limit`() {
        val method = ChatClientLoggingAdvisor::class.java.getDeclaredMethod("shortenForLog", String::class.java)
        method.isAccessible = true

        val shortText = "Short text"
        val result = method.invoke(advisor, shortText) as String

        assertThat(result).isEqualTo(shortText)
    }

    @Test
    fun `shortenForLog should truncate long text with head and tail`() {
        val method = ChatClientLoggingAdvisor::class.java.getDeclaredMethod("shortenForLog", String::class.java)
        method.isAccessible = true

        // Create text longer than HEAD_LEN + TAIL_LEN (100 + 200 = 300)
        val longText = "A".repeat(500)
        val result = method.invoke(advisor, longText) as String

        assertThat(result).contains("...")
        assertThat(result.length).isLessThan(longText.length)
        assertThat(result).startsWith("A".repeat(100))
        assertThat(result).endsWith("A".repeat(200))
    }
}
