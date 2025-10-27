package com.bftcom.docgenerator.api.dto.graph

data class GNode(
    val id: String,            // nodeId (UUID/Long → toString)
    val label: String,         // имя класса/метода/файла
    val kind: String,          // NodeKind
    val group: String?,        // пакет/модуль/namespace
    val size: Int? = null,     // для визуального веса (напр. count relations)
    val color: String? = null, // если хочешь окраску на бэке
    val meta: Map<String, Any?> = emptyMap()
)
