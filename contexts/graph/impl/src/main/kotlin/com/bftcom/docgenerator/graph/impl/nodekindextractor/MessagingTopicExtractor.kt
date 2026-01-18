package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

@Component
class MessagingTopicExtractor : NodeKindExtractor {
    override fun id() = "messaging-topic"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        if (base != NodeKind.CLASS) return null

        val imps = (ctx.imports ?: emptyList()).map { it.lowercase() }
        val name = raw.simpleName.lowercase()
        val pkg = (raw.pkgFqn ?: "").lowercase()

        val hasMqImport = MQ_IMPORT_MARKERS.any { marker -> imps.any { it.contains(marker) } }
        val hasMqPackage = MQ_PACKAGE_MARKERS.any { marker -> pkg.contains(marker) }
        val hasMqName = MQ_NAME_SUFFIXES.any { suffix -> name.endsWith(suffix) }

        return when {
            // Кейс 1: Есть MQ импорты И (имя подходящее ИЛИ пакет подходящий)
            // Это защитит RegularClass в обычном пакете.
            hasMqImport && (hasMqName || hasMqPackage) -> NodeKind.TOPIC

            // Кейс 2: Явный технический MQ пакет И подходящее имя (даже без импортов, напр. интерфейсы)
            hasMqPackage && hasMqName -> NodeKind.TOPIC

            // Кейс 3: Особо сильные маркеры в импортах (напр. аннотации слушателей),
            // но только если имя хоть как-то намекает на логику обработки.
            hasMqImport && name.contains("handler") -> NodeKind.TOPIC

            else -> null
        }
    }

    companion object {
        private val MQ_IMPORT_MARKERS = listOf("kafka", "rabbitmq", "nats", "amqp", "jms", "activemq", "pulsar")
        private val MQ_PACKAGE_MARKERS = listOf(".kafka", ".messaging", ".mq", ".queue", ".stream")
        private val MQ_NAME_SUFFIXES = listOf("consumer", "producer", "listener", "subscriber", "publisher", "receiver", "sender")
    }
}
