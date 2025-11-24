package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.RagQueryMetadata
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import com.bftcom.docgenerator.rag.api.RagSource
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

    override fun ask(query: String, sessionId: String): RagResponse {
        // Обрабатываем запрос через цепочку advisors
        val processingContext = queryProcessingChain.process(query, sessionId)
        
        // Используем обработанный запрос для поиска
        val processedQuery = processingContext.currentQuery
        val searchResults = embeddingSearchService.searchByText(processedQuery, topK = 5)

        // Если есть расширенные запросы, можно использовать их для дополнительного поиска
        val expandedQueries = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXPANDED_QUERIES)
            ?: emptyList<Any>()

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