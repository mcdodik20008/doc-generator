package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryProcessingContext

interface QueryStep {
    val type: ProcessingStepType

    fun execute(context: QueryProcessingContext): StepResult
}
