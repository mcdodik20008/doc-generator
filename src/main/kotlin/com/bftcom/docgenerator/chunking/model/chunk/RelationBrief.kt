package com.bftcom.docgenerator.chunking.model.chunk

data class RelationBrief(
    val id: String,
    val kind: String,
    val otherNodeId: String,
)
