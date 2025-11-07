package com.bftcom.docgenerator.chunking.model.chunk

data class ChunkDetailsResponse(
    val id: String,
    val title: String?,
    val node: NodeBrief?,
    val content: String?,
    val metadata: Map<String, Any?>?,
    val embeddingSize: Int?,
    val relations: ChunkRelations,
)
