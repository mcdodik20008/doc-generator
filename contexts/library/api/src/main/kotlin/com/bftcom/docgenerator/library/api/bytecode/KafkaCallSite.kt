package com.bftcom.docgenerator.library.api.bytecode

/**
 * Информация о Kafka-вызове, найденном в байткоде.
 * Фаза 1: поиск реальных Kafka-вызовов (Producer/Consumer).
 */
data class KafkaCallSite(
    /** Метод, в котором найден вызов */
    val methodId: MethodId,
    /** Топик (может быть константным или шаблоном) */
    val topic: String?,
    /** Тип операции (PRODUCE, CONSUME) */
    val operation: String,
    /** Тип клиента (KafkaProducer, KafkaConsumer) */
    val clientType: String,
    /** Дополнительные метаданные */
    val metadata: Map<String, Any> = emptyMap(),
)
