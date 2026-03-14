package com.bftcom.docgenerator.ai.advisor

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
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

    private fun defaultProps(coderDebug: Boolean = false, talkerDebug: Boolean = false) =
        AiClientsProperties(
            coder = AiClientsProperties.ClientProps(
                model = "test-coder", system = "sys", debug = coderDebug,
            ),
            talker = AiClientsProperties.ClientProps(
                model = "test-talker", system = "sys", debug = talkerDebug,
            ),
        )

    @BeforeEach
    fun setUp() {
        advisor = ChatClientLoggingAdvisor(defaultProps())
    }

    @Test
    fun `getOrder should return 0`() {
        assertThat(advisor.getOrder()).isEqualTo(0)
    }

    @Test
    fun `before should return same request without modification`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.instructions).thenReturn(
            listOf(SystemMessage("system"), UserMessage("user prompt"))
        )
        whenever(prompt.options).thenReturn(ChatOptions.builder().model("test-model").build())

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `before should handle exception when extracting prompt contents`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.instructions).thenThrow(RuntimeException("Test exception"))

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `before should use unknown model when model extraction fails`() {
        whenever(chatClientRequest.prompt()).thenReturn(prompt)
        whenever(prompt.options).thenThrow(RuntimeException("Test exception"))
        whenever(prompt.instructions).thenReturn(listOf(UserMessage("Test")))

        val result = advisor.before(chatClientRequest, advisorChain)

        assertThat(result).isSameAs(chatClientRequest)
    }

    @Test
    fun `after should return same response without modification`() {
        whenever(chatClientResponse.chatResponse()).thenReturn(chatResponse)
        whenever(chatResponse.metadata).thenReturn(metadata)
        whenever(chatResponse.result).thenReturn(Generation(AssistantMessage("Test response")))
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
        val newAdvisor = ChatClientLoggingAdvisor(defaultProps())
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
        assertThat(advisor.shortenForLog("   ")).isEqualTo("<empty>")
    }

    @Test
    fun `shortenForLog should return full text if shorter than limit`() {
        val shortText = "Short text"
        assertThat(advisor.shortenForLog(shortText)).isEqualTo(shortText)
    }

    @Test
    fun `shortenForLog should truncate long text with head and tail`() {
        val longText = "A".repeat(500)
        val result = advisor.shortenForLog(longText)

        assertThat(result).contains("...")
        assertThat(result.length).isLessThan(longText.length)
        assertThat(result).startsWith("A".repeat(120))
        assertThat(result).endsWith("A".repeat(120))
    }

    @Test
    fun `shortenForLog should replace newlines with spaces`() {
        val textWithNewlines = "line1\nline2\rline3"
        assertThat(advisor.shortenForLog(textWithNewlines)).doesNotContain("\n", "\r")
    }
}
