package com.bftcom.docgenerator.library.api.bytecode

/**
 * Сводка по методу (Фаза 3).
 * Содержит информацию о всех интеграционных вызовах (HTTP/Kafka/Camel),
 * которые могут быть выполнены из этого метода (прямо или через вызовы других методов).
 */
data class MethodSummary(
    /** Идентификатор метода */
    val methodId: MethodId,
    /** Список URL'ов, которые могут быть вызваны из этого метода */
    val urls: Set<String> = emptySet(),
    /** Список HTTP-методов */
    val httpMethods: Set<String> = emptySet(),
    /** Есть ли retry в цепочке */
    val hasRetry: Boolean = false,
    /** Есть ли timeout в цепочке */
    val hasTimeout: Boolean = false,
    /** Есть ли circuit breaker в цепочке */
    val hasCircuitBreaker: Boolean = false,
    /** Прямые HTTP-вызовы в этом методе */
    val directHttpCalls: List<HttpCallSite> = emptyList(),
    /** Список Kafka-топиков (для Producer/Consumer) */
    val kafkaTopics: Set<String> = emptySet(),
    /** Прямые Kafka-вызовы в этом методе */
    val directKafkaCalls: List<KafkaCallSite> = emptyList(),
    /** Список Camel URI endpoints */
    val camelUris: Set<String> = emptySet(),
    /** Прямые Camel-вызовы в этом методе */
    val directCamelCalls: List<CamelCallSite> = emptyList(),
    /** Является ли этот метод "родительским клиентом" */
    val isParentClient: Boolean = false,
    /** Дополнительные метаданные */
    val metadata: Map<String, Any> = emptyMap(),
)

