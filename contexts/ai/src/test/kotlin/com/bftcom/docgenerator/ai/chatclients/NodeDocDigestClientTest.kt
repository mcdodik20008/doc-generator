package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class NodeDocDigestClientTest {

    @Mock
    private lateinit var directLlm: DirectLlmClient

    private val props = AiClientsProperties(
        coder = AiClientsProperties.ClientProps(model = "test-coder", temperature = 0.1, topP = 0.9, seed = 42),
        talker = AiClientsProperties.ClientProps(model = "test-talker"),
    )

    @Test
    fun `generate should call DirectLlmClient with correct parameters`() {
        val client = NodeDocDigestClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("kind=CLASS\nfqn=com.example.Test")

        val result = client.generate("CLASS", "com.example.Test", "Technical documentation", listOf("com.example.Dep1", "com.example.Dep2"))

        assertThat(result).contains("kind=CLASS")
        verify(directLlm).call(captor.capture())
        val req = captor.firstValue
        assertThat(req.model).isEqualTo("test-coder")
        assertThat(req.userPrompt).contains("NODE_KIND=CLASS")
        assertThat(req.userPrompt).contains("FQN=com.example.Test")
        assertThat(req.userPrompt).contains("Technical documentation")
        assertThat(req.userPrompt).contains("com.example.Dep1,com.example.Dep2")
    }

    @Test
    fun `generate should handle empty dependencies`() {
        val client = NodeDocDigestClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("kind=CLASS")

        client.generate("CLASS", "com.example.Test", "Tech", emptyList())

        verify(directLlm).call(captor.capture())
        assertThat(captor.firstValue.userPrompt).contains("DEPS=none")
    }

    @Test
    fun `generate should truncate long dependencies list`() {
        val client = NodeDocDigestClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("ok")

        val deps = (1..100).map { "com.example.Dep$it" }
        client.generate("CLASS", "fqn", "tech", deps)

        verify(directLlm).call(captor.capture())
        val depsLine = captor.firstValue.userPrompt.lines().find { it.startsWith("DEPS=") } ?: ""
        assertThat(depsLine.length).isLessThanOrEqualTo(800 + 5)
    }

    @Test
    fun `generate should use coder model settings`() {
        val client = NodeDocDigestClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("ok")

        client.generate("METHOD", "fqn", "tech", emptyList())

        verify(directLlm).call(captor.capture())
        val req = captor.firstValue
        assertThat(req.model).isEqualTo("test-coder")
        assertThat(req.temperature).isEqualTo(0.1)
        assertThat(req.topP).isEqualTo(0.9)
        assertThat(req.seed).isEqualTo(42)
    }
}
