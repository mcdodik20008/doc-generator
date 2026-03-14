package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.bftcom.docgenerator.ai.resilience.ResilientExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class OllamaCoderClientTest {

    @Mock
    private lateinit var directLlm: DirectLlmClient

    private val props = AiClientsProperties(
        coder = AiClientsProperties.ClientProps(model = "test-coder", temperature = 0.1, topP = 0.9, seed = 42),
        talker = AiClientsProperties.ClientProps(model = "test-talker"),
    )

    @Test
    fun `generate should call DirectLlmClient with correct model and prompts`() {
        val client = OllamaCoderClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("Explanation")

        val result = client.generate("public class Test {}", "You are a code explainer")

        assertThat(result).isEqualTo("Explanation")
        verify(directLlm).call(captor.capture())
        val req = captor.firstValue
        assertThat(req.model).isEqualTo("test-coder")
        assertThat(req.systemPrompt).isEqualTo("You are a code explainer")
        assertThat(req.userPrompt).isEqualTo("public class Test {}")
        assertThat(req.temperature).isEqualTo(0.1)
        assertThat(req.topP).isEqualTo(0.9)
        assertThat(req.seed).isEqualTo(42)
    }

    @Test
    fun `generate should return empty string when LLM returns empty`() {
        val client = OllamaCoderClient(directLlm, props)
        whenever(directLlm.call(any())).thenReturn("")

        val result = client.generate("Context", "System")

        assertThat(result).isEmpty()
    }

    @Test
    fun `generate should reject blank context`() {
        val client = OllamaCoderClient(directLlm, props)
        assertThatThrownBy { client.generate("", "System") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generate should reject blank system prompt`() {
        val client = OllamaCoderClient(directLlm, props)
        assertThatThrownBy { client.generate("Context", "") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generate should use resilient executor when available`() {
        val resilientExecutor = mock<ResilientExecutor>()
        val client = OllamaCoderClient(directLlm, props, resilientExecutor)
        whenever(resilientExecutor.executeString(any(), any())).thenReturn("Resilient result")

        val result = client.generate("Context", "System")

        assertThat(result).isEqualTo("Resilient result")
        verify(resilientExecutor).executeString(eq("coder-generate"), any())
    }
}
