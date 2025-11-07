package com.bftcom.docgenerator.chunking.model.plan

data class PipelinePlan(
    val stages: List<String>, // ["extract-snippet","summarize","embed",...]
    val params: Map<String, Any> = emptyMap(), // лёгкие параметры (без тяжёлых данных)
    val service: ServiceMeta = ServiceMeta(),
)
