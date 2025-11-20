package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import com.bftcom.docgenerator.rag.api.RagSource
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class RagServiceImpl(
    private val embeddingSearchService: EmbeddingSearchService,
    @Qualifier("coderChatClient")
    private val chatClient: ChatClient,
) : RagService {

    override fun ask(query: String): RagResponse {
        val searchResults = embeddingSearchService.searchByText(query, topK = 5)

        val context = searchResults.joinToString("\n\n") { "Source (ID: ${it.id}):\n${it.content}" }

        val prompt =
            """
            Ты — умный ассистент разработчика. Ответь на вопрос, используя только предоставленный контекст.
            Если в контексте нет информации, так и скажи.
            
            Контекст:
            $context
            
            Вопрос:
            $query
            """.trimIndent()

        val response =
            chatClient
                .prompt()
                .user(prompt)
                .call()
                .content() ?: "Не удалось получить ответ."

        return RagResponse(
            answer = response,
            sources =
                searchResults.map {
                    RagSource(it.id, it.content, it.metadata, it.similarity)
                },
        )
    }
}
