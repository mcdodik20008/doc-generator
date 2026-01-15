package com.bftcom.docgenerator.ai.chatclients

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Генерация `doc_tech` для node_doc (техническая документация на узел).
 */
@Component
class NodeDocTechClient(
    @param:Qualifier("coderChatClient")
    private val chat: ChatClient,
) {
    private val systemPrompt =
        """
        Ты — строгий инженер-документатор.
        Твоя задача: по предоставленному контексту узла сгенерировать `doc_tech`.
        Пиши ТОЛЬКО на русском. Не выдумывай факты. Если данных не хватает — явно укажи ограничения.

        Формат ответа: Markdown, без лишних вступлений.
        Структура (если применимо):
        - Назначение
        - Контракт (входы/выходы)
        - Поведение (ключевые шаги)
        - Побочные эффекты (I/O, БД, сеть)
        - Исключения/ошибки
        - Зависимости (как используются)
        - Нюансы/ограничения
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

