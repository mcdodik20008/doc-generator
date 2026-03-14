package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class OllamaTalkerClientTest {

    @Mock
    private lateinit var directLlm: DirectLlmClient

    private val props = AiClientsProperties(
        coder = AiClientsProperties.ClientProps(model = "test-coder"),
        talker = AiClientsProperties.ClientProps(model = "test-talker", temperature = 0.7, topP = 0.95, seed = 10),
    )

    @Test
    fun `rewrite should call DirectLlmClient with talker model and user prompt`() {
        val client = OllamaTalkerClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("Human readable description")

        val request = TalkerRewriteRequest(
            nodeFqn = "com.example.Test",
            language = "ru",
            rawContent = "Technical description",
        )
        val result = client.rewrite(request)

        assertThat(result).isEqualTo("Human readable description")
        verify(directLlm).call(captor.capture())
        val req = captor.firstValue
        assertThat(req.model).isEqualTo("test-talker")
        assertThat(req.userPrompt).contains("com.example.Test")
        assertThat(req.userPrompt).contains("ru")
        assertThat(req.userPrompt).contains("Technical description")
        assertThat(req.temperature).isEqualTo(0.7)
        assertThat(req.topP).isEqualTo(0.95)
        assertThat(req.seed).isEqualTo(10)
    }

    @Test
    fun `rewrite should remove think tags from response`() {
        val client = OllamaTalkerClient(directLlm, props)
        whenever(directLlm.call(any())).thenReturn("<think>Some reasoning here</think>\nHuman readable description\n<think>More reasoning</think>")

        val result = client.rewrite(TalkerRewriteRequest("fqn", "ru", "content"))

        assertThat(result).isEqualTo("Human readable description")
        assertThat(result).doesNotContain("<think>")
        assertThat(result).doesNotContain("Some reasoning here")
    }

    @Test
    fun `rewrite should trim whitespace from response`() {
        val client = OllamaTalkerClient(directLlm, props)
        whenever(directLlm.call(any())).thenReturn("  Description  ")

        val result = client.rewrite(TalkerRewriteRequest("fqn", "ru", "content"))

        assertThat(result).isEqualTo("Description")
    }

    @Test
    fun `rewrite should trim rawContent in user prompt`() {
        val client = OllamaTalkerClient(directLlm, props)
        val captor = argumentCaptor<DirectLlmClient.LlmRequest>()
        whenever(directLlm.call(any())).thenReturn("ok")

        client.rewrite(TalkerRewriteRequest("fqn", "ru", "  Technical description  "))

        verify(directLlm).call(captor.capture())
        assertThat(captor.firstValue.userPrompt).contains("Technical description")
        assertThat(captor.firstValue.userPrompt).doesNotContain("  Technical description  ")
    }
}
