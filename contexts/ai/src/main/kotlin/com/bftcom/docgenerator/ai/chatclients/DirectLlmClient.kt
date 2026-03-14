package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Прямой HTTP-клиент к Ollama (OpenAI-compatible API).
 * Обходит Spring AI ChatClient/RetryTemplate/RestClientCustomizer —
 * использует наш httpComponentsClientHttpRequestFactory напрямую (таймаут ровно 60 мин).
 */
@Component
class DirectLlmClient(
    @Value("\${spring.ai.openai.base-url}") baseUrl: String,
    factory: ClientHttpRequestFactory,
    private val objectMapper: ObjectMapper,
    props: AiClientsProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader("Content-Type", "application/json")
        .build()

    /** Множество моделей, для которых включён debug-лог (полный текст промпта/ответа). */
    private val debugModels: Set<String> = buildSet {
        if (props.coder.debug) add(props.coder.model)
        if (props.talker.debug) add(props.talker.model)
    }

    data class LlmRequest(
        val model: String,
        val systemPrompt: String,
        val userPrompt: String,
        val temperature: Double? = null,
        val topP: Double? = null,
        val seed: Int? = null,
    )

    fun call(request: LlmRequest): String {
        val systemLen = request.systemPrompt.length
        val userLen = request.userPrompt.length
        val totalChars = systemLen + userLen
        val estimatedTokens = (totalChars / 3.5).toInt()
        val debug = request.model in debugModels

        val sb = StringBuilder()
        sb.append("LLM request started:")
        sb.append("\n  model=${request.model}, chars=$totalChars (system=$systemLen, user=$userLen), estimatedTokens=~$estimatedTokens")
        if (debug) {
            sb.append("\n  full prompt:\n").append(request.userPrompt)
        } else {
            sb.append("\n  preview: ").append(shortenForLog(request.userPrompt))
        }
        log.info("{}", sb.toString())

        val body = buildRequestBody(request)
        val startMs = System.currentTimeMillis()

        val responseJson = restClient.post()
            .uri("/v1/chat/completions")
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: throw RuntimeException("Empty response from LLM")

        val durationMs = System.currentTimeMillis() - startMs
        val (content, usage) = extractResponse(responseJson)

        val rb = StringBuilder()
        rb.append("LLM response received:")
        rb.append("\n  model=${request.model}, durationMs=$durationMs")
        rb.append(", tokens[prompt=${usage?.promptTokens}, completion=${usage?.completionTokens}, total=${usage?.totalTokens}]")
        rb.append(", chars=${content.length}")
        if (debug) {
            rb.append("\n  full response:\n").append(content)
        } else {
            rb.append("\n  preview: ").append(shortenForLog(content))
        }
        log.info("{}", rb.toString())

        return content
    }

    private fun buildRequestBody(request: LlmRequest): Map<String, Any?> {
        val messages = listOf(
            mapOf("role" to "system", "content" to request.systemPrompt),
            mapOf("role" to "user", "content" to request.userPrompt),
        )
        return buildMap {
            put("model", request.model)
            put("messages", messages)
            put("stream", false)
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("top_p", it) }
            request.seed?.let { put("seed", it) }
        }
    }

    private data class Usage(val promptTokens: Long?, val completionTokens: Long?, val totalTokens: Long?)

    private fun extractResponse(json: String): Pair<String, Usage?> {
        val tree = objectMapper.readTree(json)
        val content = tree.path("choices").path(0).path("message").path("content").asText("")
        val usageNode = tree.path("usage")
        val usage = if (usageNode.isMissingNode) null else Usage(
            promptTokens = usageNode.path("prompt_tokens").asLong(0),
            completionTokens = usageNode.path("completion_tokens").asLong(0),
            totalTokens = usageNode.path("total_tokens").asLong(0),
        )
        return content to usage
    }

    companion object {
        private const val HEAD_LEN = 120
        private const val TAIL_LEN = 120
    }

    private fun shortenForLog(text: String): String {
        if (text.isBlank()) return "<empty>"
        val clean = text.replace('\n', ' ').replace('\r', ' ')
        if (clean.length <= HEAD_LEN + TAIL_LEN) return clean
        return clean.substring(0, HEAD_LEN) + " ... " + clean.substring(clean.length - TAIL_LEN)
    }
}
