package com.bftcom.docgenerator.core.model

data class NodeDocDraft(
    val locale: String = "ru",
    val summary: String,
    val details: String,
    val paramsJson: Map<String, Any?>? = null,
    val returnsJson: Map<String, Any?>? = null,
    val throwsJson: List<Map<String, Any?>>? = null,
    val examples: String? = null,
)
