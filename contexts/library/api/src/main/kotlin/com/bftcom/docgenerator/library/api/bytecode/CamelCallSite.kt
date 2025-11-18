package com.bftcom.docgenerator.library.api.bytecode

/**
 * Информация о Camel-вызове, найденном в байткоде.
 * Фаза 1: поиск реальных Camel-вызовов (Routes, Endpoints).
 */
data class CamelCallSite(
    /** Метод, в котором найден вызов */
    val methodId: MethodId,
    /** URI endpoint (например, "kafka:topic", "http://host/path") */
    val uri: String?,
    /** Тип endpoint (kafka, http, jms, file и т.д.) */
    val endpointType: String?,
    /** Направление (FROM, TO) */
    val direction: String,
    /** Дополнительные метаданные */
    val metadata: Map<String, Any> = emptyMap(),
)

