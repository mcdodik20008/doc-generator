package com.bftcom.docgenerator.api.rag.client

import com.bftcom.docgenerator.api.rag.dto.EvaluationResult
import com.bftcom.docgenerator.config.DocEvaluatorProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

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
     * Оценивает качество документации (реактивный вариант).
     *
     * @param codeSnippet исходный код
     * @param generatedDoc сгенерированная документация
     * @return Mono с результатом оценки, пустой Mono в случае ошибки
     */
    fun evaluateAsync(
        codeSnippet: String,
        generatedDoc: String,
    ): Mono<EvaluationResult> {
        if (codeSnippet.length < 5 || generatedDoc.length < 5) {
            log.warn("Code snippet or generated doc too short for evaluation")
            return Mono.empty()
        }

        val request = EvaluateRequest(codeSnippet, generatedDoc)

        log.debug("Sending evaluation request to {}/evaluate", properties.baseUrl)

        return webClient
            .post()
            .uri("/evaluate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(EvaluationResult::class.java)
            .timeout(Duration.ofSeconds(properties.timeout))
            .doOnNext { log.info("Evaluation successful: score={}", it.finalScore) }
            .onErrorResume(WebClientResponseException::class.java) { e ->
                log.error(
                    "Doc-evaluator API error: status={}, body={}",
                    e.statusCode,
                    e.responseBodyAsString,
                )
                Mono.empty()
            }.onErrorResume { e ->
                log.error("Failed to evaluate documentation: {}", e.message, e)
                Mono.empty()
            }
    }

    /**
     * Оценивает качество документации (блокирующий вариант для совместимости).
     *
     * @param codeSnippet исходный код
     * @param generatedDoc сгенерированная документация
     * @return результат оценки или null в случае ошибки
     */
    fun evaluate(
        codeSnippet: String,
        generatedDoc: String,
    ): EvaluationResult? = evaluateAsync(codeSnippet, generatedDoc).block()

    /**
     * Проверяет доступность сервиса.
     *
     * @return Mono<Boolean> — true если сервис доступен
     */
    fun healthCheckAsync(): Mono<Boolean> =
        webClient
            .get()
            .uri("/health")
            .retrieve()
            .bodyToMono(Map::class.java)
            .timeout(Duration.ofSeconds(5))
            .map {
                log.info("Doc-evaluator service is healthy")
                true
            }.onErrorResume { e ->
                log.warn("Doc-evaluator service is unavailable: {}", e.message)
                Mono.just(false)
            }

    /**
     * Проверяет доступность сервиса (блокирующий вариант).
     *
     * @return true если сервис доступен
     */
    fun healthCheck(): Boolean = healthCheckAsync().block() ?: false
}
