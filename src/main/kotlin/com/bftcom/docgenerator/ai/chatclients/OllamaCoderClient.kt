package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.CoderExplainRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class OllamaCoderClient(
    @param:Qualifier("coderChatClient")
    private val chat: ChatClient,
) {
    private val SYSTEM_PROMPT =
        """
        Ты — опытный старший разработчик (Senior Developer) и IT-архитектор.
        Твоя задача — проанализировать предоставленный фрагмент кода и дать по нему структурированное,
        технически грамотное и лаконичное объяснение на РУССКОМ ЯЗЫКЕ.

        ## Правила:
        1.  **Точность:** Основывай свой анализ ИСКЛЮЧИТЕЛЬНО на предоставленной информации (код, FQN, подсказки).
        2.  **Без домыслов:** Не делай предположений о функциональности, которая явно не видна в коде. Если информации недостаточно для полного ответа, прямо укажи на это.
        3.  **Язык:** Ответ должен быть на русском языке.
        4.  **Формат:** Ответ должен быть в формате Markdown.

        ## Структура ответа
        Ты ОБЯЗАН предоставить ответ, используя СТРОГО следующие разделы и заголовки:

        ### 1. Краткое описание
        (Что это? Опиши назначение этого кода (класс, метод, поле, функция) одним-двумя предложениями.)

        ### 2. Основная логика
        (Пошаговое описание того, что делает код. 
        - Если это метод/функция: опиши его алгоритм, параметры и возвращаемое значение.
        - Если это класс: опиши его основные обязанности (responsibilities) и ключевые публичные методы/поля.)

        ### 3. Контекст и связи
        (Как этот код, вероятно, вписывается в систему? Используй "Расположение (FQN)" и "Подсказки"
        для предположений о его роли. Например: "Это DTO, используемый для...", "Это метод сервисного слоя...",
        "Это компонент Spring, отвечающий за...")
        """.trimIndent()

    fun explain(req: CoderExplainRequest): String {
        val userMessage =
            buildString {
                appendLine("## Данные для анализа")
                appendLine("* **Язык:** ${req.language}")
                appendLine("* **Расположение (FQN):** ${req.nodeFqn}")
                if (!req.hints.isNullOrBlank()) {
                    appendLine("* **Дополнительные подсказки (Hints):**")
                    appendLine(req.hints)
                }
                appendLine("* **Фрагмент кода:**")
                appendLine("```${req.language.lowercase()}")
                appendLine(req.codeExcerpt.trim())
                appendLine("```")
            }

        val result =
            chat
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content()
        return result.orEmpty().trim()
    }
}
