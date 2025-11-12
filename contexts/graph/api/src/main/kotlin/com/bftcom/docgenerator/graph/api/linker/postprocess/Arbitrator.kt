
package com.bftcom.docgenerator.graph.api.linker.postprocess

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

interface Arbitrator {
    fun arbitrate(edges: Sequence<EdgeProposal>): Sequence<EdgeProposal>
}
