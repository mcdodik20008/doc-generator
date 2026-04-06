package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.cache.RagCacheService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class VectorSearchStep(
    private val embeddingSearchService: EmbeddingSearchService,
    private val ragCacheService: RagCacheService,
    @Value("\${docgen.rag.search-mode:HYBRID}")
    private val searchMode: String = "HYBRID",
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.VECTOR_SEARCH

    override fun execute(context: QueryProcessingContext): StepResult {
        val originalQuery = context.originalQuery
        val rewrittenQuery = context.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)
            ?.takeIf { it.isNotBlank() && it != originalQuery }
        val applicationId = context.getMetadata<Long>(QueryMetadataKeys.APPLICATION_ID)

        // Check cache first
        val cached = ragCacheService.getCachedEmbeddingResults(originalQuery, applicationId)
        if (cached != null) {
            log.info("VECTOR_SEARCH: cache hit, {} results", cached.size)
            val updatedContext = context
                .setMetadata(QueryMetadataKeys.CHUNKS, cached)
                .addStep(
                    ProcessingStep(
                        advisorName = "VectorSearchStep",
                        input = originalQuery,
                        output = "Из кэш��: ${cached.size} чанков",
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )
            return StepResult(context = updatedContext, transitionKey = "SUCCESS")
        }

        val results = mutableListOf<SearchResult>()

        val mode = searchMode.uppercase()
        when (mode) {
            "HYBRID" -> {
                results.addAll(embeddingSearchService.hybridSearch(originalQuery, topK = 5, applicationId = applicationId))
                if (rewrittenQuery != null) {
                    results.addAll(embeddingSearchService.hybridSearch(rewrittenQuery, topK = 5, applicationId = applicationId))
                }
            }
            "FTS" -> {
                // FTS-only: use hybridSearch with ftsWeight=1, vectorWeight=0
                results.addAll(embeddingSearchService.hybridSearch(originalQuery, topK = 5, applicationId = applicationId, ftsWeight = 1.0, vectorWeight = 0.0))
                if (rewrittenQuery != null) {
                    results.addAll(embeddingSearchService.hybridSearch(rewrittenQuery, topK = 5, applicationId = applicationId, ftsWeight = 1.0, vectorWeight = 0.0))
                }
            }
            else -> {
                // VECTOR (default fallback)
                results.addAll(embeddingSearchService.searchByText(originalQuery, topK = 5, applicationId = applicationId))
                if (rewrittenQuery != null) {
                    results.addAll(embeddingSearchService.searchByText(rewrittenQuery, topK = 5, applicationId = applicationId))
                }
            }
        }

        // HyDE: поиск по гипотетическому коду для концептуальных запросов
        val hypotheticalCode = context.getMetadata<String>(QueryMetadataKeys.HYPOTHETICAL_CODE)
        if (!hypotheticalCode.isNullOrBlank()) {
            results.addAll(embeddingSearchService.searchByText(hypotheticalCode, topK = 3, applicationId = applicationId))
        }

        val mergedResults = results.distinctBy { it.id }

        // Populate cache
        ragCacheService.cacheEmbeddingResults(originalQuery, applicationId, mergedResults)

        val updatedContext = context
            .setMetadata(QueryMetadataKeys.CHUNKS, mergedResults)
            .addStep(
                ProcessingStep(
                    advisorName = "VectorSearchStep",
                    input = rewrittenQuery ?: originalQuery,
                    output = "Найдено чанков: ${mergedResults.size} (режим: $mode)",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("VECTOR_SEARCH: mode={}, original='{}', rewritten='{}', hyde={}, chunks={}", mode, originalQuery, rewrittenQuery, hypotheticalCode != null, mergedResults.size)
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.RERANKING,
        )
    }
}
