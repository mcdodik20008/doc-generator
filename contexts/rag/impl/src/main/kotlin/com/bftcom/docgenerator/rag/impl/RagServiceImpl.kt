package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.api.RagPreparedContext
import com.bftcom.docgenerator.rag.api.RagQueryMetadata
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import com.bftcom.docgenerator.rag.api.RagSource
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory.DEFAULT_CONVERSATION_ID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class RagServiceImpl(
    @Qualifier("ragChatClient")
    private val chatClient: ChatClient,
    private val graphRequestProcessor: GraphRequestProcessor,
    @Value("\${docgen.rag.max-node-code-chars:3000}") private val maxNodeCodeChars: Int = 3000,
    @Value("\${docgen.rag.max-context-chars:30000}") private val maxContextChars: Int = 30000,
    @Value("\${docgen.rag.max-exact-nodes:5}") private val maxExactNodes: Int = 5,
    @Value("\${docgen.rag.max-neighbor-nodes:10}") private val maxNeighborNodes: Int = 10,
    @Value("\${docgen.rag.processing-timeout-seconds:45}") private val processingTimeoutSeconds: Long = 45,
    @Value("\${docgen.rag.llm-timeout-seconds:30}") private val llmTimeoutSeconds: Long = 30,
    @Value("\${docgen.rag.max-graph-relations-chars:5000}") private val maxGraphRelationsChars: Int = 5000,
) : RagService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun ask(
        query: String,
        sessionId: String,
        applicationId: Long?,
    ): RagResponse {
        val prepared = prepareContext(query, sessionId, applicationId)

        val prompt = prepared.prompt
        val answer =
            if (prompt != null) {
                callLlm(prompt, prepared.sessionId)
            } else {
                prepared.fallbackAnswer ?: "Не удалось получить ответ."
            }

        return RagResponse(
            answer = answer,
            sources = prepared.sources,
            metadata = prepared.metadata,
        )
    }

    override fun prepareContext(
        query: String,
        sessionId: String,
        applicationId: Long?,
    ): RagPreparedContext = doPrepareContext(query, sessionId, applicationId, null)

    override fun prepareContextWithProgress(
        query: String,
        sessionId: String,
        applicationId: Long?,
        stepCallback: com.bftcom.docgenerator.rag.api.StepProgressCallback,
    ): RagPreparedContext = doPrepareContext(query, sessionId, applicationId, stepCallback)

    private fun doPrepareContext(
        query: String,
        sessionId: String,
        applicationId: Long?,
        stepCallback: com.bftcom.docgenerator.rag.api.StepProgressCallback?,
    ): RagPreparedContext {
        require(query.isNotBlank()) { "Query cannot be blank" }
        require(query.length <= 10000) { "Query length cannot exceed 10000 characters, got ${query.length}" }
        require(sessionId.isNotBlank()) { "Session ID cannot be blank" }

        val processingContext =
            try {
                CompletableFuture
                    .supplyAsync {
                        graphRequestProcessor.process(query, sessionId, applicationId, stepCallback)
                    }.orTimeout(processingTimeoutSeconds, TimeUnit.SECONDS)
                    .get()
            } catch (e: TimeoutException) {
                log.error("Query processing timed out after {}s: query='{}'", processingTimeoutSeconds, query.take(50))
                return RagPreparedContext(
                    prompt = null,
                    fallbackAnswer = "Не удалось обработать запрос — превышено время ожидания.",
                    sources = emptyList(),
                    metadata = RagQueryMetadata(originalQuery = query),
                    sessionId = sessionId,
                )
            } catch (e: Exception) {
                val cause = e.cause ?: e
                log.error("Query processing failed: {}", cause.message, cause)
                return RagPreparedContext(
                    prompt = null,
                    fallbackAnswer = "Произошла ошибка при обработке запроса.",
                    sources = emptyList(),
                    metadata = RagQueryMetadata(originalQuery = query),
                    sessionId = sessionId,
                )
            }
        val processingStatus = processingContext.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS)

        if (processingStatus == ProcessingStepType.FAILED.name) {
            return RagPreparedContext(
                prompt = null,
                fallbackAnswer = "Информация не найдена",
                sources = emptyList(),
                metadata = buildMetadata(processingContext),
                sessionId = sessionId,
            )
        }

        val searchResults = buildSearchResults(processingContext)
        log.info("RAG search: итоговых результатов = {}", searchResults.size)

        val exactNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
        val exactNodesContext =
            if (exactNodes != null && exactNodes.isNotEmpty()) {
                val nodes = exactNodes.filterIsInstance<Node>().take(maxExactNodes)
                if (nodes.isNotEmpty()) {
                    log.debug("Добавляем {} найденных узлов в контекст (лимит {})", nodes.size, maxExactNodes)
                    formatNodes(nodes)
                } else {
                    ""
                }
            } else {
                ""
            }

        val neighborNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.NEIGHBOR_NODES)
        val neighborNodesContext =
            if (neighborNodes != null && neighborNodes.isNotEmpty()) {
                val nodes = neighborNodes.filterIsInstance<Node>().take(maxNeighborNodes)
                if (nodes.isNotEmpty()) {
                    log.debug("Добавляем {} соседних узлов в контекст (лимит {})", nodes.size, maxNeighborNodes)
                    formatNodes(nodes)
                } else {
                    ""
                }
            } else {
                ""
            }

        val graphRelationsText = processingContext.getMetadata<String>(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)
        val archContext = processingContext.getMetadata<String>(QueryMetadataKeys.ARCHITECTURE_CONTEXT_TEXT)
        val stacktraceContext = processingContext.getMetadata<String>(QueryMetadataKeys.STACKTRACE_ANALYSIS_TEXT)

        val context =
            buildString {
                if (!archContext.isNullOrBlank()) {
                    append("=== АРХИТЕКТУРНЫЙ КОНТЕКСТ ===\n")
                    append(archContext)
                    append("\n\n")
                }
                if (!stacktraceContext.isNullOrBlank()) {
                    append("=== АНАЛИЗ СТЕКТРЕЙСА ===\n")
                    append(stacktraceContext)
                    append("\n\n")
                }
                if (exactNodesContext.isNotEmpty()) {
                    append("=== ТОЧНО НАЙДЕННЫЕ УЗЛЫ ===\n")
                    append(exactNodesContext)
                    append("\n\n")
                }
                if (neighborNodesContext.isNotEmpty()) {
                    append("=== СОСЕДНИЕ УЗЛЫ (связанные через граф) ===\n")
                    append(neighborNodesContext)
                    append("\n\n")
                }
                if (!graphRelationsText.isNullOrBlank()) {
                    append("=== СВЯЗИ В ГРАФЕ КОДА ===\n")
                    if (graphRelationsText.length > maxGraphRelationsChars) {
                        log.warn("Graph relations text truncated: {} -> {} chars", graphRelationsText.length, maxGraphRelationsChars)
                        append(graphRelationsText.take(maxGraphRelationsChars))
                        append("\n... [связи обрезаны]")
                    } else {
                        append(graphRelationsText)
                    }
                    append("\n\n")
                }
                if (exactNodesContext.isNotEmpty() || neighborNodesContext.isNotEmpty()) {
                    append("=== РЕЗУЛЬТАТЫ ВЕКТОРНОГО ПОИСКА ===\n")
                }
                append(searchResults.joinToString("\n\n") { "Source [${it.id}]:\n${it.content}" })
            }

        val truncatedContext =
            if (context.length > maxContextChars) {
                log.warn("RAG context truncated: {} -> {} chars", context.length, maxContextChars)
                context.take(maxContextChars) + "\n... [контекст обрезан]"
            } else {
                context
            }

        val queryIntent = processingContext.getMetadata<String>(QueryMetadataKeys.QUERY_INTENT)
        val systemPrompt =
            when (queryIntent) {
                "ARCHITECTURE" -> {
                    """
                    Ты — архитектурный эксперт. Ответь на вопрос об архитектуре, используя предоставленный контекст.
                    Описывай слои, компоненты, интеграции и паттерны, которые видишь в контексте.
                    Если в контексте нет информации, так и скажи.
                    ОБЯЗАТЕЛЬНО указывай источники информации в формате [ID].
                    """.trimIndent()
                }

                "STACKTRACE" -> {
                    """
                    Ты — эксперт по отладке Java/Kotlin. Проанализируй стектрейс и контекст кода.
                    Объясни причину ошибки и предложи исправление. Укажи конкретные файлы и строки.
                    Если в контексте нет информации, так и скажи.
                    ОБЯЗАТЕЛЬНО указывай источники информации в формате [ID].
                    """.trimIndent()
                }

                else -> {
                    """
                    Ты — умный ассистент разработчика. Ответь на вопрос, используя только предоставленный контекст.
                    Если в контексте нет информации, так и скажи.
                    ОБЯЗАТЕЛЬНО указывай источники информации в формате [ID], где ID — это идентификатор источника из контекста.
                    Пример: "Этот метод используется в классе Foo [123]".
                    """.trimIndent()
                }
            }

        val prompt =
            """
            $systemPrompt

            Контекст:
            $truncatedContext

            Вопрос:
            ${processingContext.originalQuery}
            """.trimIndent()

        log.info(
            "RAG prompt generated: query='{}', context_length={}, prompt_length={}",
            processingContext.originalQuery.take(80),
            truncatedContext.length,
            prompt.length,
        )

        val sources =
            searchResults.map {
                RagSource(it.id, it.content, it.metadata, it.similarity)
            }

        return RagPreparedContext(
            prompt = prompt,
            fallbackAnswer = null,
            sources = sources,
            metadata = buildMetadata(processingContext),
            sessionId = sessionId,
        )
    }

    private fun callLlm(
        prompt: String,
        sessionId: String,
    ): String =
        try {
            CompletableFuture
                .supplyAsync {
                    chatClient
                        .prompt()
                        .user(prompt)
                        .advisors { spec ->
                            spec.param(
                                DEFAULT_CONVERSATION_ID,
                                sessionId,
                            )
                        }.call()
                        .content()
                }.orTimeout(llmTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally { ex ->
                    log.error("LLM call failed or timed out: ${ex.message}", ex)
                    null
                }.get()
                ?: "Не удалось получить ответ."
        } catch (e: Exception) {
            log.error("Error during LLM call: ${e.message}", e)
            "Не удалось получить ответ."
        }

    private fun buildSearchResults(context: QueryProcessingContext): List<SearchResult> {
        val filteredChunks =
            context
                .getMetadata<List<*>>(QueryMetadataKeys.FILTERED_CHUNKS)
                ?.filterIsInstance<SearchResult>()
                .orEmpty()
        val rawChunks =
            context
                .getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
                ?.filterIsInstance<SearchResult>()
                .orEmpty()

        val selected = if (filteredChunks.isNotEmpty()) filteredChunks else rawChunks
        return selected
            .distinctBy { it.id }
            .sortedByDescending { it.similarity }
            .take(5)
    }

    private fun buildMetadata(context: QueryProcessingContext): RagQueryMetadata {
        val expandedQueries =
            context
                .getMetadata<List<*>>(QueryMetadataKeys.EXPANDED_QUERIES)
                ?.mapNotNull { it as? String }
                ?: emptyList()

        return RagQueryMetadata(
            originalQuery = context.originalQuery,
            rewrittenQuery = context.getMetadata(QueryMetadataKeys.REWRITTEN_QUERY),
            expandedQueries = expandedQueries,
            processingSteps = context.processingSteps.toList(),
            additionalData = context.metadata.toMap(),
        )
    }

    /**
     * Форматирует список узлов для включения в контекст RAG
     */
    private fun formatNodes(nodes: List<Node>): String =
        nodes.joinToString("\n\n") { node ->
            buildString {
                append("Node [${node.id}]:\n")
                append("FQN: ${node.fqn}\n")
                append("Kind: ${node.kind}\n")
                if (node.name != null) {
                    append("Name: ${node.name}\n")
                }
                if (node.signature != null) {
                    append("Signature: ${node.signature}\n")
                }
                if (node.sourceCode != null) {
                    val code = node.sourceCode
                    if (code != null) {
                        val truncatedCode =
                            if (code.length > maxNodeCodeChars) {
                                code.take(maxNodeCodeChars) + "\n// ... [код обрезан]"
                            } else {
                                code
                            }
                        append("Source Code:\n$truncatedCode\n")
                    }
                }
                if (node.docComment != null) {
                    append("Documentation:\n${node.docComment}\n")
                }
            }
        }
}
