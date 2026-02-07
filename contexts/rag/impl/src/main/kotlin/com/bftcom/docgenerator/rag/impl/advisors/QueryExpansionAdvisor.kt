package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Advisor для расширения запроса (query expansion).
 * Генерирует дополнительные варианты запроса для более широкого поиска.
 */
@Component
class QueryExpansionAdvisor(
    @param:Qualifier("ragChatClient")
    private val chatClient: ChatClient,
) : QueryProcessingAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_EXPANSION_VARIANTS = 3
        private const val LLM_TIMEOUT_SECONDS = 30L
    }

    override fun getName(): String = "QueryExpansion"

    override fun getOrder(): Int = 20

    override fun process(context: QueryProcessingContext): Boolean {
        val currentQuery = context.currentQuery

        if (currentQuery.isBlank()) {
            log.debug("Skipping query expansion: query is blank")
            return true
        }

        // Пропускаем расширение, если уже было выполнено
        if (context.hasMetadata(QueryMetadataKeys.EXPANDED)) {
            return true
        }

        val expansionPrompt = """
                        Для следующего запроса сгенерируй 2-3 альтернативные формулировки, которые могут помочь найти релевантную информацию.
                        Каждая формулировка должна быть на отдельной строке.
                        Используй синонимы, связанные термины и альтернативные способы выражения того же вопроса.

                        Оригинальный запрос: $currentQuery

                        Альтернативные формулировки (по одной на строку):
                """.trimIndent()

        val expansionResult = try {
            CompletableFuture.supplyAsync {
                chatClient
                    .prompt()
                    .user(expansionPrompt)
                    .call()
                    .content()
                    ?.trim()
            }
                .orTimeout(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally { ex ->
                    log.warn("Query expansion LLM call failed or timed out: ${ex.message}")
                    null
                }
                .get()
                ?: ""
        } catch (e: Exception) {
            log.error("Error during query expansion LLM call: ${e.message}", e)
            ""
        }

        val expandedQueries = expansionResult
            .lines()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .replace(Regex("^\\d+[.)\\s]+"), "")
                    .trim()
            }
            .filter { it.isNotBlank() && it != currentQuery }
            .take(MAX_EXPANSION_VARIANTS)

        if (expandedQueries.isNotEmpty()) {
            context.setMetadata(QueryMetadataKeys.EXPANDED, true)
            context.setMetadata(QueryMetadataKeys.EXPANDED_QUERIES, expandedQueries)
            context.addStep(
                ProcessingStep(
                    advisorName = getName(),
                    input = currentQuery,
                    output = expandedQueries.joinToString("\n"),
                ),
            )
        } else {
            log.debug("Query expansion produced no results for query: '{}'", currentQuery.take(50))
        }
        // NOTE: Consider caching expansion results for repeated identical queries

        return true
    }
}
