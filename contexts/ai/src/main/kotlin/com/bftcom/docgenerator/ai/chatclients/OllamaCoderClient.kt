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
    private val SYSTEM_PROMPT =
        """
        Ты — опытный Senior Developer и IT-архитектор.
        Объясняй код строго по фактам из запроса. Пиши на РУССКОМ.

        ПРАВИЛА:
        - Не придумывай отсутствующие детали/зависимости/поля.
        - Если данных мало — прямо скажи, чего не хватает.
        - Соблюдай формат Markdown и заголовки строго как ниже.
        - Не повторяй одно и то же в разных разделах.
        - Если фрагмент пуст или неинформативен — дай краткое описание ограничений.

        СТРУКТУРА ОТВЕТА:
        ### 1. Краткое описание
        ### 2. Основная логика
        ### 3. Контекст и связи

        ФОРМАТИРОВАНИЕ:
        - Кодовые примеры — только в бэктиках ```kotlin.
        - Не вставляй импортов в примеры, если это не критично для понимания.
        - Списки держи компактными (≤7 пунктов).
        """.trimIndent()

    fun explain(req: CoderExplainRequest): String {
        val userMessage = buildUserMessage(req)

        val result =
            chat
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content()
        return result.orEmpty().trim()
    }

    fun buildUserMessage(req: CoderExplainRequest): String {
        val hintsBlock =
            if (!req.hints.isNullOrBlank()) {
                buildString {
                    appendLine("* **Подсказки:**")
                    appendLine(req.hints.trim())
                }
            } else {
                ""
            }

        val excerptSafe = req.codeExcerpt.trim().replace("```", "``\\`")
        val span =
            when {
                req.lineStart != null && req.lineEnd != null -> " (${req.lineStart}..${req.lineEnd})"
                else -> ""
            }

        return buildString {
            appendLine("## Данные для анализа")
            appendLine("* **Язык:** ${req.language}")
            appendLine("* **Расположение (FQN):** ${req.nodeFqn}$span")
            if (hintsBlock.isNotBlank()) appendLine(hintsBlock)
            appendLine("* **Фрагмент кода:**")
            appendLine("```kotlin")
            appendLine(excerptSafe)
            appendLine("```")
            appendLine()
            appendLine("## Требование к ответу")
            appendLine("Дай ответ строго в трёх разделах, без дополнительных заголовков.")
        }
    }
}
