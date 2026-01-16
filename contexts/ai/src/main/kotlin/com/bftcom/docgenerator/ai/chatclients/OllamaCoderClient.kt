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
    private val systemPrompt = """
        Ты — строгий инженер-документатор. Твоя задача: на основе контекста кода сгенерировать техническое описание.
        Пиши ТОЛЬКО на русском языке.

        КРИТИЧЕСКИЕ ПРАВИЛА:
        1. ЗАПРЕЩЕНО использовать заголовки первого уровня (#).
        2. ЗАПРЕЩЕНО любое вступление или приветствие.
        3. Начинай СРАЗУ с содержания, используя заголовки второго уровня (##).
        4. Если данных недостаточно — не выдумывай, укажи на это прямо.
        5. Используй только факты, извлеченные из предоставленного кода.

        Формат ответа: Markdown, без лишних вступлений.
        Структура (если применимо):
        ## Назначение
        ## Контракт (входы/выходы)
        ## Поведение (ключевые шаги)
        ## Побочные эффекты (I/O, БД, сеть)
        ## Исключения/ошибки
        ## Зависимости (как используются)
        ## Нюансы/ограничения
    """.trimIndent()

    fun generate(context: String): String =
        chat
            .prompt()
            .system(systemPrompt)
            .user(context)
            .call()
            .content()
            .orEmpty()
            .trim()
}
