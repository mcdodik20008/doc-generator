package com.bftcom.docgenerator.library.api.integration

/**
 * Интеграционная точка - HTTP endpoint, Kafka topic, Camel route и т.д.
 */
sealed class IntegrationPoint {
    abstract val url: String?
    abstract val methodId: String
    
    /**
     * HTTP endpoint
     */
    data class HttpEndpoint(
        override val url: String?,
        override val methodId: String,
        val httpMethod: String?,
        val clientType: String,
        val hasRetry: Boolean = false,
        val hasTimeout: Boolean = false,
        val hasCircuitBreaker: Boolean = false,
    ) : IntegrationPoint()
    
    /**
     * Kafka topic
     */
    data class KafkaTopic(
        override val methodId: String,
        val topic: String?,
        val operation: String,
        val clientType: String,
    ) : IntegrationPoint() {
        override val url: String? = topic
    }
    
    /**
     * Camel route
     */
    data class CamelRoute(
        override val methodId: String,
        val uri: String?,
        val endpointType: String?,
        val direction: String,
    ) : IntegrationPoint() {
        override val url: String? = uri
    }
}

