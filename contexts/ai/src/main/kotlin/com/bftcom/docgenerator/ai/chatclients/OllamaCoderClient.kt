package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.CoderExplainRequest
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
) {
    fun generate(context: String, systemPrompt: String): String =
        chat
            .prompt()
            .system(systemPrompt)
            .user(context)
            .call()
            .content()
            .orEmpty()
            .trim()
}
