package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
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
) : RagService {

    override fun ask(query: String, sessionId: String): RagResponse {
        val searchResults = embeddingSearchService.searchByText(query, topK = 5)

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
            $query
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

        return RagResponse(
            answer = response,
            sources =
                searchResults.map {
                    RagSource(it.id, it.content, it.metadata, it.similarity)
                },
        )
    }
}