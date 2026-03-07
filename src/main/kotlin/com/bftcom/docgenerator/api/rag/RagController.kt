package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.api.rag.client.DocEvaluatorClient
import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.api.rag.dto.ValidatedRagResponse
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.rag.api.RagService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory.DEFAULT_CONVERSATION_ID
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@RestController
@RequestMapping("/api/v1/rag")
class RagController(
        private val ragService: RagService,
        private val docEvaluatorClient: DocEvaluatorClient,
        private val nodeRepository: NodeRepository,
        @Qualifier("ragChatClient") private val chatClient: ChatClient,
        private val objectMapper: ObjectMapper,
        private val aiClientsProperties: AiClientsProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Фильтрует сообщения об ошибках, удаляя чувствительные данные перед отправкой клиенту
     */
    private fun sanitizeErrorMessage(exception: Exception): String {
        return when (exception) {
            is IllegalArgumentException,
            is IllegalStateException -> exception.message ?: "Invalid request"
            is java.sql.SQLException -> "Database error occurred"
            is java.net.ConnectException,
            is java.net.SocketTimeoutException -> "External service unavailable"
            else -> "An error occurred while processing your request"
        }
    }

    companion object {
        private const val ASK_TIMEOUT_SECONDS = 60L
        private const val ASK_WITH_VAL_TIMEOUT_SECONDS = 90L
    }

    @PostMapping("/ask")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    fun ask(@RequestBody @Valid request: RagRequest): RagResponse {
        log.info("RAG request received: sessionId=${request.sessionId}, query_length=${request.query.length}")
        return try {
            CompletableFuture.supplyAsync {
                ragService.ask(request.query, request.sessionId, request.applicationId)
            }.orTimeout(ASK_TIMEOUT_SECONDS, TimeUnit.SECONDS).get()
        } catch (e: TimeoutException) {
            log.error("RAG request timed out for sessionId=${request.sessionId}")
            throw IllegalStateException("Request timed out after ${ASK_TIMEOUT_SECONDS} seconds")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            log.error("RAG request failed for sessionId=${request.sessionId}: ${cause.message}", cause)
            throw IllegalStateException(sanitizeErrorMessage(cause as? Exception ?: e), e)
        }
    }

    @PostMapping("/ask-with-val")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    fun askWithValidation(@RequestBody @Valid request: RagRequest): Mono<ValidatedRagResponse> {
        log.info("RAG validation request received: sessionId=${request.sessionId}, query_length=${request.query.length}")

        return Mono.fromCallable {
            ragService.ask(request.query, request.sessionId, request.applicationId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { ragResponse ->
                val firstSource = ragResponse.sources.firstOrNull()
                    ?: return@flatMap Mono.just(
                        ValidatedRagResponse(null, ragResponse, "No sources in RAG response")
                    )

                val nodeId = firstSource.id.toLongOrNull()
                    ?: return@flatMap Mono.just(
                        ValidatedRagResponse(null, ragResponse, "Invalid node ID in source")
                    )

                val node = nodeRepository.findById(nodeId).orElse(null)
                val sourceCode = node?.sourceCode
                if (sourceCode.isNullOrBlank()) {
                    return@flatMap Mono.just(
                        ValidatedRagResponse(null, ragResponse, "Node has no source code")
                    )
                }

                docEvaluatorClient.evaluateAsync(
                    codeSnippet = sourceCode,
                    generatedDoc = ragResponse.answer
                )
                    .map { evaluation ->
                        ValidatedRagResponse(evaluation, ragResponse, null)
                    }
                    .defaultIfEmpty(
                        ValidatedRagResponse(null, ragResponse, "Doc-evaluator service unavailable or returned error")
                    )
                    .timeout(Duration.ofSeconds(ASK_WITH_VAL_TIMEOUT_SECONDS))
                    .onErrorResume { e ->
                        log.error("Doc evaluator call failed: {}", e.message, e)
                        Mono.just(
                            ValidatedRagResponse(null, ragResponse, "Doc-evaluator service unavailable or returned error")
                        )
                    }
            }
            .onErrorResume { e ->
                log.error("RAG request failed during validation flow for sessionId=${request.sessionId}: ${e.message}", e)
                Mono.error(IllegalStateException(sanitizeErrorMessage(e), e))
            }
    }

    @PostMapping("/ask/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    fun askStream(@RequestBody @Valid request: RagRequest): Flux<ServerSentEvent<String>> {
        log.info("RAG stream request received: sessionId=${request.sessionId}, query_length=${request.query.length}")

        return Mono.fromCallable {
            ragService.prepareContext(request.query, request.sessionId, request.applicationId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { prepared ->
                val sourcesEvent = ServerSentEvent.builder<String>()
                    .event("sources")
                    .data(objectMapper.writeValueAsString(prepared.sources))
                    .build()
                val metadataEvent = ServerSentEvent.builder<String>()
                    .event("metadata")
                    .data(objectMapper.writeValueAsString(prepared.metadata))
                    .build()
                val doneEvent = ServerSentEvent.builder<String>()
                    .event("done")
                    .data("")
                    .build()

                val prompt = prepared.prompt
                if (prompt == null) {
                    val fallbackToken = ServerSentEvent.builder<String>()
                        .event("token")
                        .data(prepared.fallbackAnswer ?: "Не удалось получить ответ.")
                        .build()
                    Flux.just(sourcesEvent, metadataEvent, fallbackToken, doneEvent)
                } else {
                    val promptSpec = chatClient
                        .prompt()
                        .user(prompt)
                        .advisors { spec ->
                            spec.param(DEFAULT_CONVERSATION_ID, prepared.sessionId)
                        }

                    val hasCustomOptions = request.temperature != null || request.maxTokens != null
                    if (hasCustomOptions) {
                        val optionsBuilder = OpenAiChatOptions.builder()
                        request.temperature?.let { optionsBuilder.temperature(it) }
                        request.maxTokens?.let { optionsBuilder.maxTokens(it) }
                        promptSpec.options(optionsBuilder.build())
                    }

                    val tokenStream = promptSpec
                        .stream()
                        .content()
                        .map { chunk ->
                            ServerSentEvent.builder<String>()
                                .event("token")
                                .data(chunk)
                                .build()
                        }

                    Flux.just(sourcesEvent, metadataEvent)
                        .concatWith(tokenStream)
                        .concatWith(Mono.just(doneEvent))
                }
            }
            .onErrorResume { e ->
                log.error("SSE stream error: ${e.message}", e)
                val errorEvent = ServerSentEvent.builder<String>()
                    .event("error")
                    .data(sanitizeErrorMessage(e as? Exception ?: RuntimeException(e)))
                    .build()
                Flux.just(errorEvent)
            }
    }

    @GetMapping("/settings")
    fun getSettings(): Map<String, Any?> {
        val coder = aiClientsProperties.coder
        return mapOf(
            "model" to coder.model,
            "temperature" to coder.temperature,
            "topP" to coder.topP,
        )
    }
}
