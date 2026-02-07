package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.CoderExplainRequest
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Клиент для генерации технических объяснений кода через LLM (coder этап).
 */
@Component
class OllamaCoderClient(
    @param:Qualifier("coderChatClient")
    private val chat: ChatClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TIMEOUT_SECONDS = 60L
    }

    fun generate(context: String, systemPrompt: String): String {
        require(context.isNotBlank()) { "Context cannot be blank" }
        require(systemPrompt.isNotBlank()) { "System prompt cannot be blank" }

        return try {
            CompletableFuture.supplyAsync {
                chat
                    .prompt()
                    .system(systemPrompt)
                    .user(context)
                    .call()
                    .content()
                    .orEmpty()
                    .trim()
            }
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally { ex ->
                    log.error("LLM call failed or timed out: ${ex.message}", ex)
                    ""
                }
                .get()
        } catch (e: Exception) {
            log.error("Error during LLM generate call: ${e.message}", e)
            ""
        }
    }
}
