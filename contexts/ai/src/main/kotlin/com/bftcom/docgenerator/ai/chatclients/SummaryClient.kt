package com.bftcom.docgenerator.ai.chatclients

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Клиент для создания кратких изложений (summary) длинных текстов.
 * Используется для сокращения контента перед созданием эмбеддингов.
 */
@Component
class SummaryClient(
    @param:Qualifier("coderChatClient")
    private val chat: ChatClient,
) {
    private val systemPrompt =
        """
        Твоя задача — создать краткое изложение (summary) длинного текста для создания эмбеддинга.
        
        КРИТИЧЕСКИ ВАЖНО:
        - Сохрани ключевые идеи и основную суть текста
        - Убери избыточные детали, но сохрани важную информацию
        - Пиши на том же языке, что и исходный текст
        - НЕ добавляй вступлений, заключений или комментариев типа "Вот краткое изложение:"
        - Начинай СРАЗУ с содержания
        - Цель: сократить текст примерно до 60-70% от оригинала, сохранив семантику
        - Если текст технический — сохрани важные детали реализации
        - Если текст документация — сохрани структуру и ключевые разделы
        """.trimIndent()

    fun summarize(text: String): String =
        chat
            .prompt()
            .system(systemPrompt)
            .user("Создай краткое изложение следующего текста:\n\n$text")
            .call()
            .content()
            .orEmpty()
            .trim()
}
