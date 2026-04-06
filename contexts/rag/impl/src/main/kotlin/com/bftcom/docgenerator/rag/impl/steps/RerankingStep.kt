package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.ResultFilterService
import com.bftcom.docgenerator.rag.impl.reranker.CrossEncoderReranker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RerankingStep(
    private val resultFilterService: ResultFilterService,
    private val crossEncoderReranker: CrossEncoderReranker,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.RERANKING

    override fun execute(context: QueryProcessingContext): StepResult {
        val chunks = context.getMetadata<List<*>>(QueryMetadataKeys.CHUNKS)
            ?.filterIsInstance<SearchResult>()
            .orEmpty()

        val filteredChunks = if (chunks.isNotEmpty()) {
            val distinctChunks = chunks.distinctBy { it.id }
            // Cross-encoder reranking (opt-in), then keyword filtering
            val reranked = if (crossEncoderReranker.isEnabled()) {
                crossEncoderReranker.rerank(context.currentQuery, distinctChunks)
            } else {
                distinctChunks
            }
            resultFilterService.filterResults(reranked, context)
        } else {
            emptyList()
        }

        val updatedContext = context
            .setMetadata(QueryMetadataKeys.FILTERED_CHUNKS, filteredChunks)
            .addStep(
                ProcessingStep(
                    advisorName = "RerankingStep",
                    input = context.currentQuery,
                    output = "После фильтрации: ${filteredChunks.size}",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        val hasExactNodes = context.hasMetadata(QueryMetadataKeys.EXACT_NODES)
        val hasGraphText = context.getMetadata<String>(QueryMetadataKeys.GRAPH_RELATIONS_TEXT)
            ?.isNotBlank() == true

        val transitionKey = if (filteredChunks.isEmpty() && !hasExactNodes && !hasGraphText) {
            log.info("RERANKING: пустой контекст, завершаем с FAILED")
            "EMPTY"
        } else {
            "SUCCESS"
        }

        log.info("RERANKING: chunks={} -> filtered={}", chunks.size, filteredChunks.size)
        return StepResult(
            context = updatedContext,
            transitionKey = transitionKey,
        )
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.COMPLETED,
            "EMPTY" to ProcessingStepType.FAILED,
        )
    }
}
