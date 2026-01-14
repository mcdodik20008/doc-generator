package com.bftcom.docgenerator.api.rag.client

import com.bftcom.docgenerator.api.rag.dto.EvaluationResult
import com.bftcom.docgenerator.config.DocEvaluatorProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/** HTTP клиент для взаимодействия с doc-evaluator сервисом. */
@Component
class DocEvaluatorClient(
        private val properties: DocEvaluatorProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient = WebClient.builder().baseUrl(properties.baseUrl).build()

    /** Запрос для оценки документации. */
    private data class EvaluateRequest(
            @JsonProperty("code_snippet") val codeSnippet: String,
            @JsonProperty("generated_doc") val generatedDoc: String,
    )

    /**
     * Оценивает качество документации.
     *
     * @param codeSnippet исходный код
     * @param generatedDoc сгенерированная документация
     * @return результат оценки или null в случае ошибки
     */
    fun evaluate(codeSnippet: String, generatedDoc: String): EvaluationResult? {
        if (codeSnippet.length < 5 || generatedDoc.length < 5) {
            log.warn("Code snippet or generated doc too short for evaluation")
            return null
        }

        return try {
            val request = EvaluateRequest(codeSnippet, generatedDoc)

            log.debug("Sending evaluation request to {}/evaluate", properties.baseUrl)

            webClient
                    .post()
                    .uri("/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EvaluationResult::class.java)
                    .timeout(Duration.ofSeconds(properties.timeout))
                    .block()
                    .also { log.info("Evaluation successful: score={}", it?.finalScore) }
        } catch (e: WebClientResponseException) {
            log.error(
                    "Doc-evaluator API error: status={}, body={}",
                    e.statusCode,
                    e.responseBodyAsString
            )
            null
        } catch (e: Exception) {
            log.error("Failed to evaluate documentation: {}", e.message, e)
            null
        }
    }

    /**
     * Проверяет доступность сервиса.
     *
     * @return true если сервис доступен
     */
    fun healthCheck(): Boolean {
        return try {
            webClient
                    .get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .timeout(Duration.ofSeconds(5))
                    .block()

            log.info("Doc-evaluator service is healthy")
            true
        } catch (e: Exception) {
            log.warn("Doc-evaluator service is unavailable: {}", e.message)
            false
        }
    }
}
