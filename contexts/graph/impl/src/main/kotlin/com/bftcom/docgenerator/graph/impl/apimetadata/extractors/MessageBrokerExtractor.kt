package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawAnnotation
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.springframework.stereotype.Component

@Component
class MessageBrokerExtractor : ApiMetadataExtractor {
    override fun id() = "message-broker"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    private val annPattern = """@?(?:[\w.]+\.)?(\w+Listener)(?:\s*\((.*)\))?""".toRegex()

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata.MessageBrokerEndpoint? {
        // Structured annotations first
        if (function.annotations.isNotEmpty()) {
            val result = extractFromStructured(function.annotations)
            if (result != null) return result
        }
        // Fallback to regex-based extraction
        return extractFromAnnotationsRepr(function.annotationsRepr, ctx)
    }

    override fun extractTypeMetadata(type: RawType, ctx: NodeKindContext): ApiMetadata? = null

    // ===== Structured annotation extraction =====

    private fun extractFromStructured(annotations: List<RawAnnotation>): ApiMetadata.MessageBrokerEndpoint? {
        for (ann in annotations) {
            when (ann.name) {
                "KafkaListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.KAFKA,
                    topic = ann.getString("topics") ?: ann.getString("topic") ?: ann.value(),
                    consumerGroup = ann.getString("groupId") ?: ann.getString("group")
                )
                "RabbitListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.RABBITMQ,
                    queue = ann.getString("queues") ?: ann.getString("queue") ?: ann.value(),
                    exchange = ann.getString("exchange"),
                    routingKey = ann.getString("key") ?: ann.getString("routingKey")
                )
                "NatsListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.NATS,
                    topic = ann.getString("subject") ?: ann.value()
                )
                "SqsListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.SQS,
                    queue = ann.getString("queueNames") ?: ann.getString("queue") ?: ann.value()
                )
            }
        }
        return null
    }

    // ===== Regex fallback =====

    private fun extractFromAnnotationsRepr(
        annotations: Set<String>,
        ctx: NodeKindContext,
    ): ApiMetadata.MessageBrokerEndpoint? {
        for (ann in annotations) {
            val match = annPattern.matchEntire(ann) ?: continue
            val annName = match.groupValues[1]
            val content = match.groupValues.getOrNull(2) ?: ""

            when (annName) {
                "KafkaListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.KAFKA,
                    topic = extractParam(content, "topic", "topics", "value"),
                    consumerGroup = extractParam(content, "groupId", "group")
                )
                "RabbitListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.RABBITMQ,
                    queue = extractParam(content, "queues", "queue", "value"),
                    exchange = extractParam(content, "exchange"),
                    routingKey = extractParam(content, "key", "routingKey")
                )
                "NatsListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.NATS,
                    topic = extractParam(content, "subject", "value")
                )
                "SqsListener" -> return ApiMetadata.MessageBrokerEndpoint(
                    broker = ApiMetadata.BrokerType.SQS,
                    queue = extractParam(content, "queueNames", "queue", "value")
                )
            }
        }

        // Implicit NATS via imports
        val imports = ctx.imports ?: emptyList()
        if (imports.any { it.contains("io.nats") }) {
            return ApiMetadata.MessageBrokerEndpoint(
                broker = ApiMetadata.BrokerType.NATS,
                topic = null
            )
        }

        return null
    }

    private fun extractParam(content: String, vararg paramNames: String): String? {
        if (content.isBlank()) return null
        for (name in paramNames) {
            val namedPattern = """$name\s*=\s*(?:\[\s*)?["']([^"']+)["']""".toRegex()
            val match = namedPattern.find(content)
            if (match != null) return match.groupValues[1]
        }
        val simpleValuePattern = """^\s*["']([^"']+)["']\s*$""".toRegex()
        val simpleMatch = simpleValuePattern.find(content)
        if (simpleMatch != null) return simpleMatch.groupValues[1]
        return null
    }
}
