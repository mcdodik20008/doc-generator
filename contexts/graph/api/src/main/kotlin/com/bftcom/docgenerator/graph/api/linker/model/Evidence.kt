package com.bftcom.docgenerator.graph.api.linker.model

/**
 * Доказательная база для построенного ребра.
 * Хранит конкретные факты и контекст, из которых
 * детектор сделал вывод о наличии связи.
 */
data class Evidence(
    val sourceFile: String?,
    val lineNumber: Int?,
    val snippet: String?,
    val factKind: String,
    val confidence: Double = 1.0,
    val meta: Map<String, Any?> = emptyMap()
)