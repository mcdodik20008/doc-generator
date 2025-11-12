package com.bftcom.docgenerator.graph.api.linker.postprocess

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal

interface EdgeNormalizer {
    fun normalize(edge: EdgeProposal): EdgeProposal
}
