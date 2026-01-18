package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class VectorSearchStep(
    private val embeddingSearchService: EmbeddingSearchService,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.VECTOR_SEARCH

    override fun execute(context: QueryProcessingContext): StepResult {
        val originalQuery = context.originalQuery
        val rewrittenQuery = context.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)
            ?.takeIf { it.isNotBlank() && it != originalQuery }

        val results = mutableListOf<SearchResult>()
        results.addAll(embeddingSearchService.searchByText(originalQuery, topK = 5))

        if (rewrittenQuery != null) {
            results.addAll(embeddingSearchService.searchByText(rewrittenQuery, topK = 5))
        }

        val mergedResults = results.distinctBy { it.id }
        val updatedContext = context
            .setMetadata(QueryMetadataKeys.CHUNKS, mergedResults)
            .addStep(
                ProcessingStep(
                    advisorName = "VectorSearchStep",
                    input = rewrittenQuery ?: originalQuery,
                    output = "Найдено чанков: ${mergedResults.size}",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("VECTOR_SEARCH: original='{}', rewritten='{}', chunks={}", originalQuery, rewrittenQuery, mergedResults.size)
        return StepResult(ProcessingStepType.RERANKING, updatedContext)
    }
}
