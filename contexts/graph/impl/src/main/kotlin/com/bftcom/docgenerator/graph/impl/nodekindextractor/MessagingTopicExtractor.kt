package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(10)
@Component
class MessagingTopicExtractor : NodeKindExtractor {
    override fun id() = "messaging-topic"

    override fun supports(lang: Lang) = (lang == Lang.kotlin || lang == Lang.java)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val imps = NkxUtil.imps(ctx.imports)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        val hasMqImport = NkxUtil.importsContain(imps, *MQ_IMPORT_MARKERS.toTypedArray())
        val hasMqPackage = NkxUtil.pkgHasSegment(pkg, *MQ_PACKAGE_SEGMENTS.toTypedArray())
        val hasMqName = MQ_NAME_SUFFIXES.any { suffix -> n.endsWith(suffix, ignoreCase = true) }

        return when {
            hasMqImport && (hasMqName || hasMqPackage) -> NodeKind.TOPIC
            hasMqPackage && hasMqName -> NodeKind.TOPIC
            hasMqImport && NkxUtil.nameContains(n, "Handler") -> NodeKind.TOPIC
            else -> null
        }
    }

    companion object {
        private val MQ_IMPORT_MARKERS = listOf("kafka", "rabbitmq", "nats", "amqp", "jms", "activemq", "pulsar")
        private val MQ_PACKAGE_SEGMENTS = listOf("kafka", "messaging", "mq", "queue", "stream")
        private val MQ_NAME_SUFFIXES = listOf("Consumer", "Producer", "Listener", "Subscriber", "Publisher", "Receiver", "Sender")
    }
}
