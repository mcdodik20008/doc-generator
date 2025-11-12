package com.bftcom.docgenerator.graph.api.linker.model

interface Fact {
    val kind: FactKind
    val sourceNodeId: Long?
    val targetCandidate: String?
    val metadata: Map<String, Any?>
}