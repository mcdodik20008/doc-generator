package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

data class SimpleEdgeProposal(
    override val kind: EdgeKind,
    override val source: Node,
    override val target: Node,
) : EdgeProposal {
    override val evidence = emptyList<com.bftcom.docgenerator.graph.api.linker.model.Evidence>()
    override val confidence = 1.0
}
