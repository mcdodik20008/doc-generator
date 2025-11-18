package com.bftcom.docgenerator.graph.api.linker.detector

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.model.Fact

interface EdgeDetector {
    val supportedKinds: Set<EdgeKind>

    fun detect(
        facts: Sequence<Fact>,
        index: NodeIndex,
    ): Sequence<EdgeProposal>
}
