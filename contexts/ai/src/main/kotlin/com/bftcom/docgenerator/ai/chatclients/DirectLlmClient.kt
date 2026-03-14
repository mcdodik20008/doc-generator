package com.bftcom.docgenerator.ai.chatclients

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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader("Content-Type", "application/json")
        .build()

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

        log.info(
            "LLM request started: model={}, chars={} (system={}, user={}), estimatedTokens=~{}",
            request.model, totalChars, systemLen, userLen, estimatedTokens,
        )

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

        log.info(
            "LLM response received: model={}, durationMs={}, chars={}, tokens[prompt={}, completion={}, total={}]",
            request.model, durationMs, content.length,
            usage?.promptTokens, usage?.completionTokens, usage?.totalTokens,
        )

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
}
