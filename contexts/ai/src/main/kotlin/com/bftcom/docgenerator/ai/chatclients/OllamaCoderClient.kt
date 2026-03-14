package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.resilience.ResilientExecutor
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Клиент для генерации технических объяснений кода через LLM (coder этап).
 */
@Component
class OllamaCoderClient(
    @param:Qualifier("coderChatClient")
    private val chat: ChatClient,
    private val resilientExecutor: ResilientExecutor? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(context: String, systemPrompt: String): String {
        require(context.isNotBlank()) { "Context cannot be blank" }
        require(systemPrompt.isNotBlank()) { "System prompt cannot be blank" }

        val operation = {
            chat
                .prompt()
                .system(systemPrompt)
                .user(context)
                .call()
                .content()
                .orEmpty()
                .trim()
        }

        return if (resilientExecutor != null) {
            resilientExecutor.executeString("coder-generate", operation)
        } else {
            operation()
        }
    }
}
