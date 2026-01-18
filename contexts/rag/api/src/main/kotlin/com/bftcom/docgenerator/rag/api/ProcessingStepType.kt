package com.bftcom.docgenerator.rag.api

/**
 * Типы шагов графовой обработки запроса.
 */
enum class ProcessingStepType {
    EXTRACTION,
    EXACT_SEARCH,
    GRAPH_EXPANSION,
    VECTOR_SEARCH,
    RERANKING,
    COMPLETED,
    FAILED,
}
