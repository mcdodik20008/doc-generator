package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

/**
 * Извлекает метаданные message broker endpoint'ов (Kafka, RabbitMQ, NATS).
 * Поддерживает: @KafkaListener, @RabbitListener, @NatsListener
 */
@Component
class MessageBrokerExtractor : ApiMetadataExtractor {
    override fun id() = "message-broker"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata.MessageBrokerEndpoint? {
        val anns = NkxUtil.anns(function.annotationsRepr.toList())
        val imps = NkxUtil.imps(ctx.imports)

        // Kafka
        if (NkxUtil.hasAnyAnn(anns, "KafkaListener")) {
            val topic = extractTopicFromAnnotation(function.annotationsRepr, "topic", "topics")
            val groupId = extractTopicFromAnnotation(function.annotationsRepr, "groupId", "group")
            
            return ApiMetadata.MessageBrokerEndpoint(
                broker = ApiMetadata.BrokerType.KAFKA,
                topic = topic,
                consumerGroup = groupId,
            )
        }

        // RabbitMQ
        if (NkxUtil.hasAnyAnn(anns, "RabbitListener")) {
            val queue = extractTopicFromAnnotation(function.annotationsRepr, "queue", "queues")
            val exchange = extractTopicFromAnnotation(function.annotationsRepr, "exchange")
            val routingKey = extractTopicFromAnnotation(function.annotationsRepr, "routingKey", "key")
            
            return ApiMetadata.MessageBrokerEndpoint(
                broker = ApiMetadata.BrokerType.RABBITMQ,
                queue = queue,
                exchange = exchange,
                routingKey = routingKey,
            )
        }

        // NATS
        if (NkxUtil.hasAnyAnn(anns, "NatsListener") || 
            NkxUtil.importsContain(imps, "io.nats")) {
            val subject = extractTopicFromAnnotation(function.annotationsRepr, "subject")
            
            return ApiMetadata.MessageBrokerEndpoint(
                broker = ApiMetadata.BrokerType.NATS,
                topic = subject, // NATS использует subject как топик
            )
        }

        return null
    }

    override fun extractTypeMetadata(
        type: RawType,
        ctx: NodeKindContext,
    ): ApiMetadata? = null

    /**
     * Простой парсинг параметра аннотации из текста.
     * TODO: улучшить парсинг когда добавим поддержку полных аннотаций в RawFunction
     */
    private fun extractTopicFromAnnotation(
        annotations: Set<String>,
        vararg paramNames: String,
    ): String? {
        for (ann in annotations) {
            // Ищем параметр в тексте аннотации: @KafkaListener(topics = ["topic1"])
            for (paramName in paramNames) {
                // Простой regex поиск: topics = ["topic1"] или topic = "topic1"
                val patterns = listOf(
                    """$paramName\s*=\s*\["([^"]+)"""".toRegex(), // массивы
                    """$paramName\s*=\s*"([^"]+)"""".toRegex(), // строки
                    """$paramName\s*=\s*'([^']+)'""".toRegex(), // одинарные кавычки
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(ann)
                    if (match != null) return match.groupValues[1]
                }
            }
        }
        return null
    }
}

