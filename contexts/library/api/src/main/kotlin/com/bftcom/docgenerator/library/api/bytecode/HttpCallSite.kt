package com.bftcom.docgenerator.library.api.bytecode

/**
 * Информация о HTTP-вызове, найденном в байткоде.
 * Фаза 1: поиск реальных HTTP-вызовов.
 */
data class HttpCallSite(
    /** Метод, в котором найден вызов */
    val methodId: MethodId,
    /** URL (может быть константным или шаблоном) */
    val url: String?,
    /** HTTP-метод (GET, POST, PUT, DELETE и т.д.) */
    val httpMethod: String?,
    /** Тип клиента (WebClient, RestTemplate) */
    val clientType: String,
    /** Найден ли retry в цепочке */
    val hasRetry: Boolean = false,
    /** Найден ли timeout в цепочке */
    val hasTimeout: Boolean = false,
    /** Найден ли circuit breaker в цепочке */
    val hasCircuitBreaker: Boolean = false,
    /** Дополнительные метаданные */
    val metadata: Map<String, Any> = emptyMap(),
)

