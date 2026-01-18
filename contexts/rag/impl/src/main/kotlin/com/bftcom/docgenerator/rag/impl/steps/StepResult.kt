package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryProcessingContext

data class StepResult(
    val nextStep: ProcessingStepType,
    val context: QueryProcessingContext,
)