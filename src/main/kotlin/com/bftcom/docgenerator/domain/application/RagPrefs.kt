package com.bftcom.docgenerator.domain.application

data class RagPrefs(
    val priority: List<String>? = null,  // ["doc","code"]
    val reranker: String? = null         // "colbert"
)
