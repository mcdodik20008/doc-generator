package com.bftcom.docgenerator.graph.api.linker.metrics

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

interface Explainer {
    fun explain(edge: EdgeProposal): String
}
