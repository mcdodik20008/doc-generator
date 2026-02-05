package com.bftcom.docgenerator.api.embedding

import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.api.embedding.dto.AddDocumentRequest
import com.bftcom.docgenerator.api.embedding.dto.SearchRequest
import com.bftcom.docgenerator.api.embedding.dto.SearchResultResponse
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.EmbeddingStoreService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/embedding")
class EmbeddingController(
    private val searchService: EmbeddingSearchService,
    private val storeService: EmbeddingStoreService,
    private val chunkRepository: ChunkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    /**
     * Поиск документов по текстовому запросу
     */
    @PostMapping("/search")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun search(@RequestBody @Valid request: SearchRequest): List<SearchResultResponse> {
        val startTime = System.currentTimeMillis()
        log.info("Embedding search request: query_length=${request.query.length}, topK=${request.topK}")

        // TODO: Синхронный вызов может блокировать поток при большом topK
        try {
            val results = searchService.searchByText(request.query, request.topK)

            val duration = System.currentTimeMillis() - startTime
            log.info(
                "Embedding search completed: results_count=${results.size}, topK=${request.topK}, duration_ms=$duration"
            )

            // TODO: Маппинг всего списка в памяти - при большом topK может вызвать проблемы с памятью
            return results.map { result ->
                SearchResultResponse(
                    id = result.id,
                    content = result.content,
                    metadata = result.metadata,
                    similarity = result.similarity,
                )
            }
        } catch (e: IllegalArgumentException) {
            log.error("Invalid search parameters: query_length=${request.query.length}, error=${e.message}")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search parameters: ${e.message}", e)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error(
                "Embedding search failed: query_length=${request.query.length}, topK=${request.topK}, duration_ms=$duration, error=${e.message}",
                e
            )
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to perform embedding search",
                e
            )
        }
    }

    /**
     * Добавить документ с текстом (эмбеддинг будет вычислен автоматически)
     */
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited(maxRequests = 50, windowSeconds = 60)
    fun addDocument(@RequestBody @Valid request: AddDocumentRequest) {
        // TODO: Синхронный вызов - вычисление embedding может занять много времени
        try {
            log.info("Adding document: id=${request.id}, content_length=${request.content.length}")

            // Проверяем на дубликаты
            val existingDocument = storeService.getDocument(request.id)
            if (existingDocument != null) {
                log.warn("Document with id=${request.id} already exists")
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document with id=${request.id} already exists. Use PUT to update or DELETE first."
                )
            }

            storeService.addDocument(request.id, request.content, request.metadata)
            log.info("Document added successfully: id=${request.id}")
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: IllegalArgumentException) {
            log.error("Invalid document data: id=${request.id}, error=${e.message}")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document data: ${e.message}", e)
        } catch (e: Exception) {
            log.error("Failed to add document: id=${request.id}, error=${e.message}", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to compute embedding or store document",
                e
            )
        }
    }

    /**
     * Удалить документ по идентификатору
     */
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDocument(@PathVariable id: String) {
        try {
            log.info("Deleting document: id=$id")

            // Проверяем существование документа перед удалением
            val document = storeService.getDocument(id)
            if (document == null) {
                log.warn("Document not found for deletion: id=$id")
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document with id=$id not found")
            }

            storeService.deleteDocument(id)
            log.info("Document deleted successfully: id=$id")
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to delete document: id=$id, error=${e.message}", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to delete document",
                e
            )
        }
    }

    /**
     * Очистить все данные постпроцесса у всех чанков.
     * Используется при смене модели эмбеддинга или для полной переобработки.
     * Очищает: embedding, embed_model, embed_ts, content_hash, token_count
     *
     * ВНИМАНИЕ: Опасная операция! Требует подтверждения через заголовок X-Confirmation-Token
     */
    @PostMapping("/clear-postprocess")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    fun clearAllPostprocessData(
        @RequestHeader("X-Confirmation-Token", required = false) confirmationToken: String?
    ): Map<String, Any> {
        // Проверка confirmation token
        val expectedToken = "CONFIRM_CLEAR_POSTPROCESS_DATA"
        if (confirmationToken != expectedToken) {
            throw IllegalArgumentException(
                "This is a dangerous operation. Please provide confirmation token in X-Confirmation-Token header. " +
                "Expected value: $expectedToken"
            )
        }

        val startTime = System.currentTimeMillis()
        val clearedCount = chunkRepository.clearAllPostprocessData()
        val duration = System.currentTimeMillis() - startTime

        // Audit logging
        val auditMessage = "CRITICAL OPERATION: clearAllPostprocessData executed. " +
            "Cleared chunks: $clearedCount, Duration: ${duration}ms"
        org.slf4j.LoggerFactory.getLogger(javaClass).warn(auditMessage)

        // TODO: Операция может занять очень много времени при большом количестве чанков - рассмотреть батчевую обработку
        return mapOf(
            "clearedChunks" to clearedCount,
            "durationMs" to duration,
            "message" to "All postprocess data has been cleared. Chunks will be reprocessed on next postprocess run.",
        )
    }
}

