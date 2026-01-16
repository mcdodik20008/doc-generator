package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
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
        val annotations = function.annotationsRepr

        // 1. Пытаемся определить по аннотациям
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
            }
        }

        // 2. Если аннотаций нет, проверяем импорты (Implicit NATS)
        val imports = ctx.imports ?: emptyList()
        if (imports.any { it.contains("io.nats") }) {
            return ApiMetadata.MessageBrokerEndpoint(
                broker = ApiMetadata.BrokerType.NATS,
                topic = null // В данном случае топик не определен без аннотации
            )
        }

        return null
    }

    override fun extractTypeMetadata(type: RawType, ctx: NodeKindContext): ApiMetadata? = null

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