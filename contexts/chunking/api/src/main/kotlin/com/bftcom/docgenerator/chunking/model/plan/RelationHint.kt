package com.bftcom.docgenerator.chunking.model.plan

data class RelationHint(
    val kind: String, // "CALLS", "READS", ...
    val dstNodeId: Long,
    val confidence: Double = 0.7,
)
