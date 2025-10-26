package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class OllamaTalkerClient(
    @param:Qualifier("talkerChatClient")
    private val chat: ChatClient,
) {
    private val rewriteSystemPrompt =
        """
        Твоя задача — переписать техническое объяснение функции, сделав его понятным для нетехнического специалиста (например, менеджера или аналитика).

        ### Цель
        Опиши, *что* эта функция делает (ее бизнес-задачу), а не *как* она это делает (детали реализации).

        ### Правила
        1. **Пиши простым языком:** Представь, что объясняешь коллеге, который не знает код.
        2. **Без жаргона:** Категорически НЕ используй технический жаргон, имена классов (вроде `ExtTaskRunningData`, `TopicRequestDto`), имена полей или сложные IT-термины из источника. Замени их общими понятиями (например, 'данные о задаче', 'запрос', 'ошибка').
        3. **По существу:** Сохрани основную суть. Не додумывай и не добавляй факты, которых нет в источнике.
        4. **Кратко:** Уложись в 3-6 предложений.
        """.trimIndent()

    fun rewrite(req: TalkerRewriteRequest): String {
        // 1. Формируем ЮЗЕР-промпт (только переменные данные)
        val userPrompt =
            buildString {
                appendLine("### Контекст (для твоего понимания)")
                appendLine("Метод: ${req.nodeFqn}")
                appendLine("Язык: ${req.language}")
                appendLine()
                appendLine("### Техническое объяснение (источник для пересказа):")
                appendLine(req.rawContent.trim())
            }

        // 2. Вызываем chatClient с разделением
        val result =
            chat
                .prompt()
                .system(rewriteSystemPrompt) // <-- Инструкции "роли"
                .user(userPrompt) // <-- Конкретные данные
                .call()
                .content()

        return result.orEmpty().trim()
    }
}
