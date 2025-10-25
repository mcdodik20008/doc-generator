package com.bftcom.docgenerator.chunking.model

import com.bftcom.docgenerator.domain.node.Node

data class ChunkPlan(
    val source: String,
    val kind: String?,
    val content: String,
    val langDetected: String?,
    val spanLines: String?,              // "[s,e]" — закрытый int4range
    val title: String?,
    val sectionPath: List<String>,
    val relations: List<Map<String, Any>>,
    val usedObjects: List<Map<String, Any>>,
    val pipeline: Map<String, Any>,
    val node: Node
)