package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class OllamaTalkerClient(
    @param:Qualifier("talkerChatClient")
    private val chat: ChatClient,
) {
    private val thinkRegex = Pattern.compile("<think>.*?</think>", Pattern.DOTALL)

    private val rewriteSystemPrompt =
        """
        Твоя задача — переписать техническое описание компонента системы (класса, метода или процесса), сделав его понятным для нетехнического специалиста (менеджера или аналитика).

        ### Цель
        Опиши, *что* этот компонент делает (его бизнес-задачу или роль в процессе), а не *как* он это делает (детали реализации).

        ### Основные правила
        1. **Пиши простым языком:** Представь, что объясняешь коллеге, который не знает код.
        2. **По существу:** Сохрани основную суть. Не додумывай и не добавляй факты, которых нет в источнике.
        3. **Полнота и краткость:** Пиши кратко, но достаточно для полного понимания бизнес-задачи.
        
        ### КРИТИЧЕСКИ ВАЖНЫЕ ПРАВИЛА (ЯЗЫК И ФОРМАТ)

        4. **ТОЛЬКО РУССКИЙ ЯЗЫК И БЕЗ ЖАРГОНА:**
           * Весь твой ответ должен быть **на чистом русском языке**.
           * **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО:** Смешивать языки.
           * **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО (ЖАРГОН):** Не используй имена классов, имена полей, интерфейсов или IT-термины из источника.
           * **ЗАМЕНЯЙ:** Заменяй жаргон общими понятиями.
           * **ИСКЛЮЧЕНИЕ:** Можешь использовать общепринятые аббревиатуры (API, JSON, SQL) или названия технологий (Spring, Gradle), *только* если их невозможно адекватно перевести или заменить общим понятием.

        5. **НИКАКИХ ОБДУМЫВАНИЙ (ЧИСТЫЙ ОТВЕТ):**
           * Твой ответ должен содержать *только* итоговый переписанный текст.
           * **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО:** Использовать теги `<think>` или `</think>`, описывать свой процесс мышления, добавлять вступления ("Вот переписанный текст:") или любой другой служебный текст.
        """.trimIndent()

    fun rewrite(req: TalkerRewriteRequest): String {
        val userPrompt =
            buildString {
                appendLine("### Контекст (для твоего понимания)")
                appendLine("Компонент: ${req.nodeFqn}")
                appendLine("Язык: ${req.language}")
                appendLine()
                appendLine("### Техническое описание (источник для пересказа):")
                appendLine(req.rawContent.trim())
            }

        val rawResult =
            chat
                .prompt()
                .system(rewriteSystemPrompt)
                .user(userPrompt)
                .call()
                .content()

        return thinkRegex.matcher(rawResult.orEmpty()).replaceAll("").trim()
    }
}