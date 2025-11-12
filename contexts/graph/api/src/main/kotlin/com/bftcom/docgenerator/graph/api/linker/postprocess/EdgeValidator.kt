package com.bftcom.docgenerator.graph.api.linker.postprocess

import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal
import com.bftcom.docgenerator.graph.api.linker.model.ValidationResult

interface EdgeValidator {
    fun validate(edge: EdgeProposal): ValidationResult
}
