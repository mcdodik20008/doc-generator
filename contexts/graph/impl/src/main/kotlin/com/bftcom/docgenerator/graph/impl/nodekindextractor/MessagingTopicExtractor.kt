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

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val imps = NkxUtil.imps(ctx.imports)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        // Kafka/Spring Kafka/Rabbit/NATS по импортам
        val mqImports = listOf("spring.kafka", "org.apache.kafka", "spring.rabbit", "io.nats")
        val mqHit = mqImports.any { NkxUtil.importsContain(imps, it) }

        if (mqHit && (
                NkxUtil.nameEnds(n, "Consumer", "Producer", "Listener") ||
                    pkg.contains(".kafka") || pkg.contains(".messag") || pkg.contains(".mq")
            )
        ) {
            return NodeKind.TOPIC
        }
        return null
    }
}
