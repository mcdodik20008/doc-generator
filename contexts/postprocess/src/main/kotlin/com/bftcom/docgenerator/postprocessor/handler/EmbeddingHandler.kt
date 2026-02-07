package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.ai.chatclients.SummaryClient
import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.OffsetDateTime
import kotlin.math.pow

@Component
class EmbeddingHandler(
    private val client: EmbeddingClient?,
    private val summaryClient: SummaryClient,
    @param:Value("\${docgen.embed.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${docgen.embed.max-content-chars:30000}")
    private val maxContentChars: Int,
    @param:Value("\${docgen.embed.retry.max-attempts:3}")
    private val maxRetryAttempts: Int = 3,
    @param:Value("\${docgen.embed.retry.initial-delay-ms:1000}")
    private val initialRetryDelayMs: Long = 1000,
) : PostprocessHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(s: ChunkSnapshot) = enabled && client != null && !s.embeddingPresent

    override fun produce(s: ChunkSnapshot): PartialMutation {
        val client = this.client
            ?: throw IllegalStateException("EmbeddingHandler.produce() called but client is null")
        val content = s.content
        val contentForEmbedding =
            if (content.length > maxContentChars) {
                log.info(
                    "EmbeddingHandler: creating summary for chunkId={}, originalLength={}, maxLength={}",
                    s.id,
                    content.length,
                    maxContentChars,
                )
                val summary = summaryClient.summarize(content)
                if (summary.length > maxContentChars) {
                    log.warn(
                        "EmbeddingHandler: summary still too long ({}), truncating to {}",
                        summary.length,
                        maxContentChars,
                    )
                    summary.take(maxContentChars)
                } else {
                    summary
                }
            } else {
                content
            }

        // Подсчитываем примерное количество токенов для логирования
        val estimatedTokens = contentForEmbedding.split(Regex("""\s+""")).size
        log.debug(
            "EmbeddingHandler: processing chunkId={}, contentLength={}, estimatedTokens={}",
            s.id,
            contentForEmbedding.length,
            estimatedTokens,
        )

        // Retry логика для сетевых ошибок
        val vec = embedWithRetry(client, contentForEmbedding, s.id)
        require(vec.size == client.dim) {
            "Embedding dim ${vec.size} != expected ${client.dim} for chunkId=${s.id}"
        }
        return PartialMutation()
            .set(FieldKey.EMB, vec)
            .set(FieldKey.EMBED_MODEL, client.modelName)
            .set(FieldKey.EMBED_TS, OffsetDateTime.now())
    }

    private fun embedWithRetry(client: EmbeddingClient, content: String, chunkId: Long): FloatArray {
        var lastException: Throwable? = null
        for (attempt in 1..maxRetryAttempts) {
            try {
                return client.embed(content)
            } catch (e: Throwable) {
                lastException = e
                val isRetryable = isRetryableError(e)

                if (!isRetryable || attempt >= maxRetryAttempts) {
                    if (isRetryable) {
                        log.error(
                            "EmbeddingHandler: failed after {} attempts for chunkId={}, error={}",
                            maxRetryAttempts,
                            chunkId,
                            e.message,
                            e,
                        )
                    } else {
                        log.error(
                            "EmbeddingHandler: non-retryable error for chunkId={}, error={}",
                            chunkId,
                            e.message,
                            e,
                        )
                    }
                    throw e
                }

                // Экспоненциальная задержка с джиттером
                val delayMs = (initialRetryDelayMs * 2.0.pow(attempt - 1)).toLong()
                val jitter = (delayMs * 0.1).toLong() // ±10% джиттер
                val actualDelay = delayMs + (Math.random() * jitter * 2 - jitter).toLong()

                log.warn(
                    "EmbeddingHandler: attempt {}/{} failed for chunkId={}, retrying in {}ms, error={}",
                    attempt,
                    maxRetryAttempts,
                    chunkId,
                    actualDelay,
                    e.message,
                )

                try {
                    Thread.sleep(actualDelay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("Interrupted during retry delay for chunk $chunkId", ie)
                }
            }
        }

        // Не должно сюда дойти, но на всякий случай
        throw IllegalStateException(
            "Failed to embed chunk $chunkId after $maxRetryAttempts attempts. Last error: ${lastException?.message}",
            lastException
        )
    }

    private fun isRetryableError(e: Throwable): Boolean {
        // Проверяем корневую причину
        var cause: Throwable? = e
        val seen = mutableSetOf<Throwable>()
        while (cause != null && !seen.contains(cause)) {
            seen.add(cause)
            when {
                // EOF ошибки - соединение разорвано
                cause is EOFException -> return true
                // Socket ошибки - проблемы с сетью
                cause is SocketException -> return true
                cause is SocketTimeoutException -> return true
                // Проверяем сообщение об ошибке на EOF
                cause.message?.contains("EOF", ignoreCase = true) == true -> return true
                cause.message?.contains("Connection reset", ignoreCase = true) == true -> return true
                cause.message?.contains("Broken pipe", ignoreCase = true) == true -> return true
            }
            cause = cause.cause
        }
        return false
    }
}
