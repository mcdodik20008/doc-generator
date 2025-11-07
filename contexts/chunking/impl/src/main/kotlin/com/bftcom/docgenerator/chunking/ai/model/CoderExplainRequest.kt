package com.bftcom.docgenerator.chunking.ai.model

data class CoderExplainRequest(
    val nodeFqn: String,
    val language: String,
    val hints: String?,
    val codeExcerpt: String,
    val lineStart: Int? = null,
    val lineEnd: Int? = null,
)
