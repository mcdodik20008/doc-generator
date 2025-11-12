package com.bftcom.docgenerator.graph.api.linker.detector

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.linker.model.Evidence

interface EdgeProposal {
    val kind: EdgeKind
    val source: Node
    val target: Node
    val evidence: List<Evidence>
    val confidence: Double
}
