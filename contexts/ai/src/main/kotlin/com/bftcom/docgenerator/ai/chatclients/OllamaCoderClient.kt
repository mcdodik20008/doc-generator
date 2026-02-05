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
    // TODO: КРИТИЧЕСКАЯ ПРОБЛЕМА - нет timeout для LLM вызова, может зависнуть навсегда
    // TODO: Нет обработки ошибок - если chat.call() упадет, весь метод упадет
    // TODO: Нет валидации входных параметров (context и systemPrompt могут быть слишком большими)
    // TODO: Синхронный вызов блокирует поток - рассмотреть использование suspend функции
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
