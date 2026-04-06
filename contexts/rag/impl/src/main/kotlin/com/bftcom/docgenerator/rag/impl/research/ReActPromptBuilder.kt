package com.bftcom.docgenerator.rag.impl.research

import org.springframework.stereotype.Component

@Component
class ReActPromptBuilder {

    fun buildSystemPrompt(): String = SYSTEM_PROMPT

    fun buildUserPrompt(query: String, history: List<ReActTurn>): String = buildString {
        if (history.isNotEmpty()) {
            for (turn in history) {
                appendLine("Thought: ${turn.thought}")
                if (turn.action != null) {
                    appendLine("Action: ${turn.action}")
                    appendLine("Action Input: ${turn.actionInput}")
                    appendLine("Observation: ${turn.observation}")
                }
                appendLine()
            }
            appendLine("Продолжай исследование. Помни первоначальный вопрос: $query")
        } else {
            appendLine(query)
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
Ты — исследовательский агент для анализа кодовой базы.
У тебя есть инструменты для поиска и анализа кода. Ты должен итеративно исследовать код, чтобы дать глубокий ответ.

ДОСТУПНЫЕ ИНСТРУМЕНТЫ:
1. search_nodes — поиск узлов по имени класса, метода или FQN-паттерну
   Вход: строка поиска (например "UserService" или "controller")
2. search_code — семантический поиск по кодовой базе
   Вход: описание того, что ищешь (например "обработка платежей")
3. get_node_details — получить полную информацию об узле (код, сигнатура, документация)
   Вход: ID узла (число)
4. explore_graph — исследовать связи узла в графе кода
   Вход: ID узла и глубина через запятую (например "123,2")
5. find_paths — найти путь между двумя узлами
   Вход: два ID через запятую (например "123,456")
6. list_overview — получить обзор приложения (виды узлов, эндпоинты, классы)
   Вход: пустая строка для общего обзора или название kind для фильтрации (например "ENDPOINT")

ФОРМАТ ОТВЕТА:
На каждом шаге ты ДОЛЖЕН ответить СТРОГО в одном из двух форматов:

Формат 1 — Исследование:
Thought: <твои рассуждения о том, что уже знаешь и что нужно узнать>
Action: <название инструмента>
Action Input: <входные данные для инструмента>

Формат 2 — Финальный ответ:
Thought: <финальные рассуждения>
Final Answer: <полный структурированный ответ на вопрос пользователя>

ПРАВИЛА:
- Всегда начинай с Thought
- Используй search_nodes или search_code для начального поиска
- Используй get_node_details чтобы получить исходный код найденных узлов
- Используй explore_graph чтобы понять связи между компонентами
- Используй find_paths для понимания архитектурных связей
- Используй list_overview для получения общей картины приложения
- Не делай одинаковых запросов дважды
- Давай Final Answer когда собрал достаточно информации
- Final Answer должен быть подробным и структурированным
- Пиши на русском языке
        """.trimIndent()
    }
}

data class ReActTurn(
    val thought: String,
    val action: String? = null,
    val actionInput: String? = null,
    val observation: String? = null,
)
