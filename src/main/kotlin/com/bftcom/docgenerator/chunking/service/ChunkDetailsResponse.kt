package com.bftcom.docgenerator.chunking.service

data class ChunkDetailsResponse(
    val id: String,
    val title: String?,
    val node: NodeBrief?,
    val content: String?,
    val metadata: Map<String, Any?>?,
    val embeddingSize: Int?,
    val relations: ChunkRelations
)

data class NodeBrief(
    val id: String,
    val kind: String,
    val name: String,
    val packageName: String?
)

data class ChunkRelations(
    val incoming: List<RelationBrief>,
    val outgoing: List<RelationBrief>
)

data class RelationBrief(
    val id: String,
    val kind: String,
    val otherNodeId: String
)
