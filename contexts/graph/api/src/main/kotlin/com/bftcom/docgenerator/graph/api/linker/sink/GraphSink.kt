package com.bftcom.docgenerator.graph.api.linker.sink

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

interface GraphSink {
    fun upsertEdges(edges: Sequence<EdgeProposal>)
}
