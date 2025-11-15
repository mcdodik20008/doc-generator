package com.bftcom.docgenerator.graph.api.apimetadata

/**
 * Метаданные API endpoint'а - HTTP, GraphQL, gRPC и т.д.
 */
sealed interface ApiMetadata {
    /**
     * HTTP endpoint - REST API
     */
    data class HttpEndpoint(
        val method: String, // GET, POST, PUT, DELETE, PATCH и т.д.
        val path: String, // /api/users/{id}
        val basePath: String? = null, // @RequestMapping на классе
        val consumes: List<String>? = null, // Content-Type
        val produces: List<String>? = null, // Accept
        val headers: Map<String, String>? = null,
    ) : ApiMetadata

    /**
     * GraphQL endpoint
     */
    data class GraphQLEndpoint(
        val query: String? = null, // название query
        val mutation: String? = null, // название mutation
        val subscription: String? = null, // название subscription
        val schema: String? = null, // путь к schema
    ) : ApiMetadata

    /**
     * gRPC endpoint
     */
    data class GrpcEndpoint(
        val service: String, // название сервиса
        val method: String, // название метода
        val packageName: String? = null, // proto package
    ) : ApiMetadata

    /**
     * Message broker endpoint - Kafka/RabbitMQ/NATS
     */
    data class MessageBrokerEndpoint(
        val broker: BrokerType, // KAFKA, RABBITMQ, NATS
        val topic: String? = null, // название топика/очереди
        val queue: String? = null,
        val consumerGroup: String? = null, // для Kafka
        val exchange: String? = null, // для RabbitMQ
        val routingKey: String? = null, // для RabbitMQ
    ) : ApiMetadata

    enum class BrokerType {
        KAFKA,
        RABBITMQ,
        NATS,
        SQS, // AWS SQS
    }
}

