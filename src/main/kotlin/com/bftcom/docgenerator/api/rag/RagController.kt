package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.api.rag.client.DocEvaluatorClient
import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.api.rag.dto.ValidatedRagResponse
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
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
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/api/rag")
class RagController(
    private val ragService: RagService,
    private val docEvaluatorClient: DocEvaluatorClient,
    private val nodeRepository: NodeRepository,
    @Qualifier("ragChatClient") private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper,
    private val aiClientsProperties: AiClientsProperties,
    private val chatSessionService: com.bftcom.docgenerator.service.ChatSessionService,
    private val userDetailsService: com.bftcom.docgenerator.service.UserDetailsServiceImpl,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Фильтрует сообщения об ошибках, удаляя чувствительные данные перед отправкой клиенту
     */
    private fun sanitizeErrorMessage(exception: Throwable): String =
        when (exception) {
            is IllegalArgumentException,
            is IllegalStateException,
            -> exception.message ?: "Invalid request"

            is java.sql.SQLException -> "Database error occurred"

            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            -> "External service unavailable"

            else -> "An error occurred while processing your request"
        }

    companion object {
        private const val ASK_TIMEOUT_SECONDS = 60L
        private const val ASK_WITH_VAL_TIMEOUT_SECONDS = 90L
    }

    @PostMapping("/ask")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    fun ask(
        @RequestBody @Valid request: RagRequest,
    ): RagResponse {
        log.info("RAG request received: sessionId=${request.sessionId}, query_length=${request.query.length}")
        return try {
            CompletableFuture
                .supplyAsync {
                    ragService.ask(request.query, request.sessionId, request.applicationId)
                }.orTimeout(ASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .get()
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
    fun askWithValidation(
        @RequestBody @Valid request: RagRequest,
    ): Mono<ValidatedRagResponse> {
        log.info("RAG validation request received: sessionId=${request.sessionId}, query_length=${request.query.length}")

        return Mono
            .fromCallable {
                ragService.ask(request.query, request.sessionId, request.applicationId)
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { ragResponse ->
                val firstSource =
                    ragResponse.sources.firstOrNull()
                        ?: return@flatMap Mono.just(
                            ValidatedRagResponse(null, ragResponse, "No sources in RAG response"),
                        )

                val nodeId =
                    firstSource.id.toLongOrNull()
                        ?: return@flatMap Mono.just(
                            ValidatedRagResponse(null, ragResponse, "Invalid node ID in source"),
                        )

                val node = nodeRepository.findById(nodeId).orElse(null)
                val sourceCode = node?.sourceCode
                if (sourceCode.isNullOrBlank()) {
                    return@flatMap Mono.just(
                        ValidatedRagResponse(null, ragResponse, "Node has no source code"),
                    )
                }

                docEvaluatorClient
                    .evaluateAsync(
                        codeSnippet = sourceCode,
                        generatedDoc = ragResponse.answer,
                    ).map { evaluation ->
                        ValidatedRagResponse(evaluation, ragResponse, null)
                    }.defaultIfEmpty(
                        ValidatedRagResponse(null, ragResponse, "Doc-evaluator service unavailable or returned error"),
                    ).timeout(Duration.ofSeconds(ASK_WITH_VAL_TIMEOUT_SECONDS))
                    .onErrorResume { e ->
                        log.error("Doc evaluator call failed: {}", e.message, e)
                        Mono.just(
                            ValidatedRagResponse(null, ragResponse, "Doc-evaluator service unavailable or returned error"),
                        )
                    }
            }.onErrorResume { e ->
                log.error("RAG request failed during validation flow for sessionId=${request.sessionId}: ${e.message}", e)
                Mono.error(IllegalStateException(sanitizeErrorMessage(e), e))
            }
    }

    @PostMapping("/ask/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    fun askStream(
        @RequestBody @Valid request: RagRequest,
    ): Flux<ServerSentEvent<String>> {
        log.info("RAG stream request received: sessionId=${request.sessionId}, query_length=${request.query.length}")

        // Сохраняем user-сообщение в начале
        saveUserMessage(request.sessionId, request.query, request.applicationId)

        // Накопитель для ответа LLM (для последующего сохранения)
        val answerBuilder = StringBuilder()
        val sourcesRef =
            java.util.concurrent.atomic
                .AtomicReference<List<com.bftcom.docgenerator.rag.api.RagSource>>()
        val metadataRef =
            java.util.concurrent.atomic
                .AtomicReference<com.bftcom.docgenerator.rag.api.RagQueryMetadata>()

        // Создаем sink для step событий
        val stepSink =
            reactor.core.publisher.Sinks
                .many()
                .multicast()
                .onBackpressureBuffer<com.bftcom.docgenerator.rag.api.StepEvent>()

        // Создаем callback для отправки step событий в sink
        val stepCallback =
            com.bftcom.docgenerator.rag.api.StepProgressCallback { event ->
                val emitResult = stepSink.tryEmitNext(event)
                if (emitResult.isFailure) {
                    log.warn("Failed to emit step event: $emitResult")
                }
            }

        // Запускаем подготовку контекста асинхронно с callback
        val preparedMono =
            Mono
                .fromCallable {
                    ragService.prepareContextWithProgress(request.query, request.sessionId, request.applicationId, stepCallback)
                }.subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(150)) // Таймаут на весь процесс подготовки
                .doOnError { e ->
                    log.error("Context preparation failed: ${e.message}", e)
                    stepSink.tryEmitError(e)
                }.doFinally {
                    // Закрываем sink после завершения
                    stepSink.tryEmitComplete()
                }.cache() // Кэшируем результат для повторного использования

        // Преобразуем step события в SSE
        val stepEvents =
            stepSink.asFlux().map { stepEvent ->
                ServerSentEvent
                    .builder<String>()
                    .event("step")
                    .data(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "type" to stepEvent.stepType.name,
                                "status" to stepEvent.status.name,
                                "description" to stepEvent.description,
                                "metadata" to stepEvent.metadata,
                                "timestamp" to stepEvent.timestamp,
                            ),
                        ),
                    ).build()
            }

        // КРИТИЧНО: Подписываемся на preparedMono сразу, чтобы начать эмитить step события
        val contentFlux =
            preparedMono.flatMapMany { prepared ->
                // Сохраняем sources и metadata для последующего сохранения в БД
                sourcesRef.set(prepared.sources)
                metadataRef.set(prepared.metadata)

                val sourcesEvent =
                    ServerSentEvent
                        .builder<String>()
                        .event("sources")
                        .data(objectMapper.writeValueAsString(prepared.sources))
                        .build()
                val metadataEvent =
                    ServerSentEvent
                        .builder<String>()
                        .event("metadata")
                        .data(objectMapper.writeValueAsString(prepared.metadata))
                        .build()
                val doneEvent =
                    ServerSentEvent
                        .builder<String>()
                        .event("done")
                        .data("")
                        .build()

                val prompt = prepared.prompt
                if (prompt == null) {
                    val fallbackAnswer = prepared.fallbackAnswer ?: "Не удалось получить ответ."
                    answerBuilder.append(fallbackAnswer)

                    val fallbackToken =
                        ServerSentEvent
                            .builder<String>()
                            .event("token")
                            .data(fallbackAnswer)
                            .build()
                    Flux.just(sourcesEvent, metadataEvent, fallbackToken, doneEvent)
                } else {
                    val promptSpec =
                        chatClient
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

                    val tokenStream =
                        promptSpec
                            .stream()
                            .content()
                            .timeout(Duration.ofSeconds(180)) // Таймаут на LLM стриминг
                            .onErrorResume { e ->
                                log.error("LLM streaming failed: ${e.message}", e)
                                Flux.just("⚠️ Ошибка генерации ответа: ${e.message}")
                            }.map { chunk ->
                                // Накапливаем токены для последующего сохранения
                                answerBuilder.append(chunk)

                                ServerSentEvent
                                    .builder<String>()
                                    .event("token")
                                    .data(chunk)
                                    .build()
                            }

                    Flux
                        .just(sourcesEvent, metadataEvent)
                        .concatWith(tokenStream)
                        .concatWith(Mono.just(doneEvent))
                }
            }

        // Объединяем step события и контент используя merge (параллельно)
        return Flux
            .merge(stepEvents, contentFlux)
            .doFinally {
                // Сохраняем assistant-ответ в БД после завершения стриминга
                val answer = answerBuilder.toString()
                val sources = sourcesRef.get()
                val metadata = metadataRef.get()

                if (answer.isNotBlank() && sources != null && metadata != null) {
                    saveAssistantMessage(request.sessionId, answer, sources, metadata)
                }
            }.onErrorResume { e ->
                log.error("SSE stream error: ${e.message}", e)
                val errorEvent =
                    ServerSentEvent
                        .builder<String>()
                        .event("error")
                        .data(
                            objectMapper.writeValueAsString(
                                mapOf("message" to sanitizeErrorMessage(e as? Exception ?: RuntimeException(e))),
                            ),
                        ).build()
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

    /**
     * Сохраняет user-сообщение в чат.
     * Создает новый чат если его нет.
     */
    private fun saveUserMessage(
        sessionId: String,
        query: String,
        applicationId: Long?,
    ): com.bftcom.docgenerator.service.ChatSessionDto? {
        return try {
            val userId = userDetailsService.getCurrentUserId().block() ?: return null

            // Получаем или создаем чат
            val chat =
                chatSessionService.getOrCreateChat(
                    sessionId = sessionId,
                    userId = userId,
                    title = generateChatTitle(query),
                )

            // Сохраняем user-сообщение
            val message =
                com.bftcom.docgenerator.service.ChatMessageDto(
                    role = "user",
                    data = mapOf("query" to query),
                    timestamp = System.currentTimeMillis(),
                )

            chatSessionService.addMessage(sessionId, userId, message)
        } catch (e: Exception) {
            log.error("Failed to save user message: ${e.message}", e)
            null
        }
    }

    /**
     * Сохраняет assistant-ответ в чат.
     */
    private fun saveAssistantMessage(
        sessionId: String,
        answer: String,
        sources: List<com.bftcom.docgenerator.rag.api.RagSource>,
        metadata: com.bftcom.docgenerator.rag.api.RagQueryMetadata,
    ) {
        try {
            val userId = userDetailsService.getCurrentUserId().block() ?: return

            val message =
                com.bftcom.docgenerator.service.ChatMessageDto(
                    role = "assistant",
                    data =
                        mapOf(
                            "answer" to answer,
                            "sources" to sources,
                            "metadata" to metadata,
                        ),
                    timestamp = System.currentTimeMillis(),
                )

            chatSessionService.addMessage(sessionId, userId, message)
            log.debug("Saved assistant message to chat: sessionId={}, answerLength={}", sessionId, answer.length)
        } catch (e: Exception) {
            log.error("Failed to save assistant message: ${e.message}", e)
        }
    }

    /**
     * Генерирует название чата на основе первого вопроса.
     */
    private fun generateChatTitle(query: String): String {
        val maxLength = 50
        return if (query.length > maxLength) {
            query.take(maxLength) + "..."
        } else {
            query
        }
    }
}
