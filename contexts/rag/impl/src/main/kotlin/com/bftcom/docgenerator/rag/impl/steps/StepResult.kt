package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.QueryProcessingContext

data class StepResult(
    val context: QueryProcessingContext,
    val transitionKey: String = "SUCCESS",
)