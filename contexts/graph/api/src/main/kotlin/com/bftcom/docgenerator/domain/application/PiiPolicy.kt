package com.bftcom.docgenerator.domain.application

data class PiiPolicy(
    val rules: Map<String, String>? = null,
)
