package com.bftcom.docgenerator.graph.impl.apimetadata.util

import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata

/**
 * Утилита для сериализации ApiMetadata в Map<String, Any> для хранения в NodeMeta.
 */
object ApiMetadataSerializer {
    /**
     * Сериализовать ApiMetadata в Map для хранения в JSONB.
     */
    fun serialize(metadata: ApiMetadata?): Map<String, Any>? {
        if (metadata == null) return null

        return when (metadata) {
            is ApiMetadata.HttpEndpoint -> mapOf(
                "@type" to "HttpEndpoint",
                "method" to metadata.method,
                "path" to metadata.path,
                "basePath" to (metadata.basePath ?: ""),
                "consumes" to (metadata.consumes ?: emptyList<String>()),
                "produces" to (metadata.produces ?: emptyList<String>()),
                "headers" to (metadata.headers ?: emptyMap<String, String>()),
            ).filterValues { it !is String || it.isNotEmpty() }
             .filterValues { it !is List<*> || it.isNotEmpty() }
             .filterValues { it !is Map<*, *> || it.isNotEmpty() }

            is ApiMetadata.GraphQLEndpoint -> mapOf(
                "@type" to "GraphQLEndpoint",
                "query" to (metadata.query ?: ""),
                "mutation" to (metadata.mutation ?: ""),
                "subscription" to (metadata.subscription ?: ""),
                "schema" to (metadata.schema ?: ""),
            ).filterValues { it is String && it.isNotEmpty() }

            is ApiMetadata.GrpcEndpoint -> mapOf(
                "@type" to "GrpcEndpoint",
                "service" to metadata.service,
                "method" to metadata.method,
                "packageName" to (metadata.packageName ?: ""),
            ).filterValues { it !is String || it.isNotEmpty() }

            is ApiMetadata.MessageBrokerEndpoint -> mapOf(
                "@type" to "MessageBrokerEndpoint",
                "broker" to metadata.broker.name,
                "topic" to (metadata.topic ?: ""),
                "queue" to (metadata.queue ?: ""),
                "consumerGroup" to (metadata.consumerGroup ?: ""),
                "exchange" to (metadata.exchange ?: ""),
                "routingKey" to (metadata.routingKey ?: ""),
            ).filterValues { it !is String || it.isNotEmpty() }
        }
    }
}

