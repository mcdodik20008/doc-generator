package com.bftcom.docgenerator.domain.dto

/**
 * DTO для cross-app графа - визуализация связей между приложениями через интеграционные точки.
 */
data class CrossAppGraphResponse(
    val nodes: List<CrossAppNode>,
    val edges: List<CrossAppEdge>,
    val statistics: CrossAppStatistics,
)

/**
 * Узел в cross-app графе.
 * Может быть приложением или интеграционной точкой (endpoint/topic).
 */
data class CrossAppNode(
    val id: String, // "app:1" или "integration:ENDPOINT:infra:http:..."
    val label: String, // "my-service" или "GET /api/users"
    val kind: String, // "APPLICATION", "ENDPOINT", "TOPIC"
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * Ребро в cross-app графе - связь между приложением и интеграционной точкой.
 */
data class CrossAppEdge(
    val source: String, // ID узла-источника
    val target: String, // ID узла-назначения
    val kind: String, // "CALLS_HTTP", "PRODUCES", "CONSUMES", "CALLS_CAMEL"
    val methodCount: Int = 1, // Количество методов, использующих эту связь
)

/**
 * Статистика по cross-app графу.
 */
data class CrossAppStatistics(
    val applicationCount: Int,
    val httpEndpoints: Int,
    val kafkaTopics: Int,
    val camelRoutes: Int,
    val totalEdges: Int,
    val apiContracts: Int = 0,
    val endpointMatches: Int = 0,
)

/**
 * Типы интеграций для фильтрации.
 */
enum class IntegrationType {
    HTTP,
    KAFKA,
    CAMEL,
    ;

    companion object {
        fun fromString(value: String): IntegrationType? = entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
