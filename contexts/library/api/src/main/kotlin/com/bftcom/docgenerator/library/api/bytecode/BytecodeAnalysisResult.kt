package com.bftcom.docgenerator.library.api.bytecode

/**
 * Результат анализа байткода библиотеки.
 * Содержит всю информацию, собранную в процессе анализа.
 */
data class BytecodeAnalysisResult(
    /** Граф вызовов методов */
    val callGraph: CallGraph,
    /** HTTP-вызовы, найденные в байткоде (Фаза 1) */
    val httpCallSites: List<HttpCallSite>,
    /** Kafka-вызовы, найденные в байткоде (Фаза 1) */
    val kafkaCallSites: List<KafkaCallSite>,
    /** Camel-вызовы, найденные в байткоде (Фаза 1) */
    val camelCallSites: List<CamelCallSite>,
    /** Сводки по методам (Фаза 3) */
    val methodSummaries: Map<MethodId, MethodSummary>,
    /** Родительские клиенты (верхнеуровневые методы) */
    val parentClients: Set<MethodId>,
)

