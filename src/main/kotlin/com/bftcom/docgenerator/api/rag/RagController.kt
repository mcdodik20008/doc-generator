package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.api.rag.client.DocEvaluatorClient
import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.api.rag.dto.ValidatedRagResponse
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RestController
@RequestMapping("/api/rag")
class RagController(
        private val ragService: RagService,
        private val docEvaluatorClient: DocEvaluatorClient,
        private val nodeRepository: NodeRepository,
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
    fun askWithValidation(@RequestBody @Valid request: RagRequest): ValidatedRagResponse {
        log.info("RAG validation request received: sessionId=${request.sessionId}, query_length=${request.query.length}")
        // Получаем RAG ответ
        val ragResponse = try {
            ragService.ask(request.query, request.sessionId, request.applicationId)
        } catch (e: Exception) {
            log.error("RAG request failed during validation flow for sessionId=${request.sessionId}: ${e.message}", e)
            throw IllegalStateException(sanitizeErrorMessage(e), e)
        }

        // Пытаемся провалидировать ответ
        var validation: com.bftcom.docgenerator.api.rag.dto.EvaluationResult? = null
        var validationError: String? = null

        try {
            // Получаем первый source (если есть) для валидации
            val firstSource = ragResponse.sources.firstOrNull()

            if (firstSource != null) {
                // Пытаемся получить node по ID
                val nodeId = firstSource.id.toLongOrNull()

                if (nodeId != null) {
                    val node = nodeRepository.findById(nodeId).orElse(null)

                    // Используем safe call и let для безопасной обработки nullable значений
                    val sourceCode = node?.sourceCode
                    if (sourceCode != null && sourceCode.isNotBlank()) {
                        // Валидируем: sourceCode как code_snippet, answer как generated_doc
                        validation = try {
                            CompletableFuture.supplyAsync {
                                docEvaluatorClient.evaluate(
                                    codeSnippet = sourceCode,
                                    generatedDoc = ragResponse.answer
                                )
                            }.orTimeout(ASK_WITH_VAL_TIMEOUT_SECONDS, TimeUnit.SECONDS).get()
                        } catch (e: TimeoutException) {
                            log.error("Doc evaluator call timed out")
                            null
                        } catch (e: Exception) {
                            log.error("Doc evaluator call failed: ${e.message}", e)
                            null
                        }

                        if (validation == null) {
                            validationError = "Doc-evaluator service unavailable or returned error"
                        }
                    } else {
                        validationError = "Node has no source code"
                    }
                } else {
                    validationError = "Invalid node ID in source"
                }
            } else {
                validationError = "No sources in RAG response"
            }
        } catch (e: Exception) {
            // Логируем полную ошибку с stack trace для отладки
            log.error("Validation failed for query: ${request.query.take(50)}...", e)
            // Возвращаем клиенту безопасное сообщение без чувствительных данных
            validationError = "Validation failed: ${sanitizeErrorMessage(e)}"
        }

        return ValidatedRagResponse(
                validation = validation,
                ragResponse = ragResponse,
                validationError = validationError
        )
    }
}
