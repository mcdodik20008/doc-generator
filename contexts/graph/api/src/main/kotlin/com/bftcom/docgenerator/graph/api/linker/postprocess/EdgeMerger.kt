package com.bftcom.docgenerator.graph.api.linker.postprocess

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

interface EdgeMerger {
    fun merge(proposals: Sequence<EdgeProposal>): Sequence<EdgeProposal>
}
