package com.bftcom.docgenerator.embedding.impl

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class EmbeddingSearchServiceImpl(
    private val vectorStore: VectorStore,
) : EmbeddingSearchService {

    override fun searchByText(query: String, topK: Int): List<SearchResult> {
        val searchRequest = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build()

        val results = vectorStore.similaritySearch(searchRequest)

        return results.map { doc: Document ->
            SearchResult(
                id = doc.id ?: "",
                content = doc.text ?: "",
                metadata = doc.metadata,
                similarity = doc.score ?: 0.0
            )
        }
    }
}