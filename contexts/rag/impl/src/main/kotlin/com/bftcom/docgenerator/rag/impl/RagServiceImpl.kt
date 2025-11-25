package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
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
    private val embeddingSearchService: EmbeddingSearchService,
    @Qualifier("ragChatClient")
    private val chatClient: ChatClient,
    private val queryProcessingChain: QueryProcessingChain,
) : RagService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun ask(query: String, sessionId: String): RagResponse {
        // Обрабатываем запрос через цепочку advisors
        val processingContext = queryProcessingChain.process(query, sessionId)
        
        // ВАЖНО: Используем оригинальный запрос для основного поиска,
        // чтобы не потерять точные названия классов/методов
        val originalQuery = processingContext.originalQuery
        log.debug("RAG search: original query = '{}'", originalQuery)
        
        val mainResults = embeddingSearchService.searchByText(originalQuery, topK = 5)
        log.debug("RAG search: main results count = {}", mainResults.size)

        // Собираем все варианты запросов для дополнительного поиска
        val allQueries = mutableListOf<String>()
        
        // Добавляем переформулированный запрос, если он есть
        val rewrittenQuery = processingContext.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)
        if (rewrittenQuery != null && rewrittenQuery != originalQuery) {
            allQueries.add(rewrittenQuery)
            log.debug("RAG search: added rewritten query = '{}'", rewrittenQuery)
        }
        
        // Добавляем расширенные запросы
        val expandedQueries = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXPANDED_QUERIES)
            ?: emptyList<Any>()
        expandedQueries.forEach { q ->
            val queryStr = q as? String
            if (queryStr != null && queryStr != originalQuery) {
                allQueries.add(queryStr)
                log.debug("RAG search: added expanded query = '{}'", queryStr)
            }
        }

        // Выполняем дополнительные поиски
        val additionalResults = allQueries.flatMap { q ->
            embeddingSearchService.searchByText(q, topK = 3)
        }
        log.debug("RAG search: additional results count = {}", additionalResults.size)

        // Объединяем результаты, убираем дубликаты по ID, сортируем по similarity
        val allResults = (mainResults + additionalResults)
            .distinctBy { it.id }
            .sortedByDescending { it.similarity }
            .take(5)

        val searchResults = allResults
        log.debug("RAG search: final results count = {}", searchResults.size)

        val context =
            searchResults.joinToString("\n\n") { "Source [${it.id}]:\n${it.content}" }

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
        val metadata = RagQueryMetadata(
            originalQuery = processingContext.originalQuery,
            rewrittenQuery = processingContext.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY),
            expandedQueries = expandedQueries.mapNotNull { it as? String },
            processingSteps = processingContext.processingSteps.toList(),
            additionalData = processingContext.metadata.toMap(),
        )

        return RagResponse(
            answer = response,
            sources =
                searchResults.map {
                    RagSource(it.id, it.content, it.metadata, it.similarity)
                },
            metadata = metadata,
        )
    }
}