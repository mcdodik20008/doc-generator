package com.bftcom.docgenerator.core.model

data class NodeDocPatch(
    val locale: String = "ru",
    val usedBy: List<String> = emptyList(), // будем добавлять в details
)
