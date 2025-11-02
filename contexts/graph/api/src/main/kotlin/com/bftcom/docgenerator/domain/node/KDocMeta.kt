package com.bftcom.docgenerator.domain.node

data class KDocMeta(
    val summary: String? = null,
    val details: String? = null,
    val tags: Map<String, String>? = null,
)
