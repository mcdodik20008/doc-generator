package com.bftcom.docgenerator.chunking.model.chunk

data class ChunkRelations(
    val incoming: List<RelationBrief>,
    val outgoing: List<RelationBrief>,
)
