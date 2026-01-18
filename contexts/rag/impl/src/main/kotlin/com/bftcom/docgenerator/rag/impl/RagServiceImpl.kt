package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.api.RagQueryMetadata
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import com.bftcom.docgenerator.rag.api.RagSource
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory.DEFAULT_CONVERSATION_ID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class RagServiceImpl(
    @Qualifier("ragChatClient")
    private val chatClient: ChatClient,
    private val graphRequestProcessor: GraphRequestProcessor,
) : RagService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun ask(query: String, sessionId: String): RagResponse {
        val processingContext = graphRequestProcessor.process(query, sessionId)
        val processingStatus = processingContext.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS)

        if (processingStatus == ProcessingStepType.FAILED.name) {
            return RagResponse(
                answer = "Информация не найдена",
                sources = emptyList(),
                metadata = buildMetadata(processingContext),
            )
        }

        val searchResults = buildSearchResults(processingContext)
        log.info("RAG search: итоговых результатов = {}", searchResults.size)

        // Добавляем найденные узлы из точного поиска в начало контекста
        val exactNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
        val exactNodesContext = if (exactNodes != null && exactNodes.isNotEmpty()) {
            val nodes = exactNodes.filterIsInstance<Node>()
            if (nodes.isNotEmpty()) {
                log.debug("Добавляем {} найденных узлов в контекст", nodes.size)
                formatNodes(nodes)
            } else {
                ""
            }
        } else {
            ""
        }

        // Добавляем соседние узлы из расширения окрестности
        val neighborNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.NEIGHBOR_NODES)
        val neighborNodesContext = if (neighborNodes != null && neighborNodes.isNotEmpty()) {
            val nodes = neighborNodes.filterIsInstance<Node>()
            if (nodes.isNotEmpty()) {
                log.debug("Добавляем {} соседних узлов в контекст", nodes.size)
                formatNodes(nodes)
            } else {
                ""
            }
        } else {
            ""
        }

        val graphRelationsText = processingContext.getMetadata<String>(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)

        val context = buildString {
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
                append(graphRelationsText)
                append("\n\n")
            }
            if (exactNodesContext.isNotEmpty() || neighborNodesContext.isNotEmpty()) {
                append("=== РЕЗУЛЬТАТЫ ВЕКТОРНОГО ПОИСКА ===\n")
            }
            append(searchResults.joinToString("\n\n") { "Source [${it.id}]:\n${it.content}" })
        }

        val prompt =
            """
            Ты — умный ассистент разработчика. Ответь на вопрос, используя только предоставленный контекст.
            Если в контексте нет информации, так и скажи.
            
            ОБЯЗАТЕЛЬНО указывай источники информации в формате [ID], где ID — это идентификатор источника из контекста.
            Пример: "Этот метод используется в классе Foo [123]".
            
            Контекст:
            $context
            
            Вопрос:
            ${processingContext.originalQuery}
            """.trimIndent()

        log.info(prompt)
        val response =
            chatClient
                .prompt()
                .user(prompt)
                .advisors { spec ->
                    spec.param(
                        DEFAULT_CONVERSATION_ID,
                        sessionId
                    )
                }
                .call()
                .content()
                ?: "Не удалось получить ответ."

        // Формируем метаданные
        return RagResponse(
            answer = response,
            sources =
                searchResults.map {
                    RagSource(it.id, it.content, it.metadata, it.similarity)
                },
            metadata = buildMetadata(processingContext),
        )
    }

    private fun buildSearchResults(context: QueryProcessingContext): List<SearchResult> {
        val filteredChunks = context.getMetadata<List<*>>(QueryMetadataKeys.FILTERED_CHUNKS)
            ?.filterIsInstance<SearchResult>()
            .orEmpty()
        val rawChunks = context.getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
            ?.filterIsInstance<SearchResult>()
            .orEmpty()

        val selected = if (filteredChunks.isNotEmpty()) filteredChunks else rawChunks
        return selected
            .distinctBy { it.id }
            .sortedByDescending { it.similarity }
            .take(5)
    }

    private fun buildMetadata(context: QueryProcessingContext): RagQueryMetadata {
        val expandedQueries = context.getMetadata<List<*>>(QueryMetadataKeys.EXPANDED_QUERIES)
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
    private fun formatNodes(nodes: List<Node>): String {
        return nodes.joinToString("\n\n") { node ->
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
                    append("Source Code:\n${node.sourceCode}\n")
                }
                if (node.docComment != null) {
                    append("Documentation:\n${node.docComment}\n")
                }
            }
        }
    }
}