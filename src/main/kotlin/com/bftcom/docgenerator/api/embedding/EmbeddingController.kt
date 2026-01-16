package com.bftcom.docgenerator.api.embedding

import com.bftcom.docgenerator.api.embedding.dto.AddDocumentRequest
import com.bftcom.docgenerator.api.embedding.dto.SearchRequest
import com.bftcom.docgenerator.api.embedding.dto.SearchResultResponse
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.EmbeddingStoreService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/embedding")
class EmbeddingController(
    private val searchService: EmbeddingSearchService,
    private val storeService: EmbeddingStoreService,
    private val chunkRepository: ChunkRepository,
) {
    /**
     * Поиск документов по текстовому запросу
     */
    @PostMapping("/search")
    fun search(@RequestBody @Valid request: SearchRequest): List<SearchResultResponse> {
        val results = searchService.searchByText(request.query, request.topK)
        return results.map { result ->
            SearchResultResponse(
                id = result.id,
                content = result.content,
                metadata = result.metadata,
                similarity = result.similarity,
            )
        }
    }

    /**
     * Добавить документ с текстом (эмбеддинг будет вычислен автоматически)
     */
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDocument(@RequestBody @Valid request: AddDocumentRequest) {
        storeService.addDocument(request.id, request.content, request.metadata)
    }

    /**
     * Удалить документ по идентификатору
     */
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDocument(@PathVariable id: String) {
        storeService.deleteDocument(id)
    }

    /**
     * Очистить все данные постпроцесса у всех чанков.
     * Используется при смене модели эмбеддинга или для полной переобработки.
     * Очищает: embedding, embed_model, embed_ts, content_hash, token_count
     */
    @PostMapping("/clear-postprocess")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun clearAllPostprocessData(): Map<String, Any> {
        val clearedCount = chunkRepository.clearAllPostprocessData()
        return mapOf(
            "clearedChunks" to clearedCount,
            "message" to "All postprocess data has been cleared. Chunks will be reprocessed on next postprocess run.",
        )
    }
}

