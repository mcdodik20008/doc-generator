package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Advisor для расширения запроса (query expansion).
 * Генерирует дополнительные варианты запроса для более широкого поиска.
 */
@Component
class QueryExpansionAdvisor(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryProcessingAdvisor {

    override fun getName(): String = "QueryExpansion"

    override fun getOrder(): Int = 20

    override fun process(context: QueryProcessingContext): Boolean {
        // TODO: Нет обработки ошибок - если метод упадет, вся цепочка обработки упадет
        // TODO: Нет валидации currentQuery (может быть пустым или слишком длинным)
        val currentQuery = context.currentQuery

        // Пропускаем расширение, если уже было выполнено
        if (context.hasMetadata(QueryMetadataKeys.EXPANDED)) {
            return true
        }

        // TODO: Prompt hardcoded в коде - должен быть в конфигурации или template файле
        // TODO: Hardcoded числа "2-3" - должны быть в конфигурации
        // TODO: Промпт на русском языке - нужна поддержка интернационализации
        val expansionPrompt = """
                        Для следующего запроса сгенерируй 2-3 альтернативные формулировки, которые могут помочь найти релевантную информацию.
                        Каждая формулировка должна быть на отдельной строке.
                        Используй синонимы, связанные термины и альтернативные способы выражения того же вопроса.

                        Оригинальный запрос: $currentQuery

                        Альтернативные формулировки (по одной на строку):
                """.trimIndent()

        // TODO: Нет обработки ошибок - если LLM недоступен, advisor упадет
        // TODO: Нет retry логики для временных ошибок
        // TODO: Нет кеширования результатов для одинаковых запросов
        val expansionResult = try {
            java.util.concurrent.CompletableFuture.supplyAsync {
                chatClient
                    .prompt()
                    .user(expansionPrompt)
                    .call()
                    .content()
                    ?.trim()
            }
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally { ex ->
                    org.slf4j.LoggerFactory.getLogger(javaClass)
                        .warn("Query expansion LLM call failed or timed out: ${ex.message}")
                    null
                }
                .get()
                ?: ""
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(javaClass)
                .error("Error during query expansion LLM call: ${e.message}", e)
            ""
        }

        // TODO: Парсинг по lines() может не сработать если LLM вернул нумерованный список или bullet points
        // TODO: Нет валидации что expandedQueries действительно релевантны исходному запросу
        // TODO: Hardcoded limit take(3) - должен быть в конфигурации
        val expandedQueries = expansionResult
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != currentQuery }
            .take(3)

        if (expandedQueries.isNotEmpty()) {
            // TODO: Нет проверки что setMetadata и addStep выполнились успешно
            context.setMetadata(QueryMetadataKeys.EXPANDED, true)
            context.setMetadata(QueryMetadataKeys.EXPANDED_QUERIES, expandedQueries)
            context.addStep(
                ProcessingStep(
                    advisorName = getName(),
                    input = currentQuery,
                    output = expandedQueries.joinToString("\n"),
                ),
            )
        }
        // TODO: Если expandedQueries пусто (LLM не смог расширить запрос), нет логирования этого случая
        // TODO: Всегда возвращается true даже если расширение не удалось - может скрыть проблемы

        return true
    }
}

