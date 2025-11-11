package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class ServiceLayerExtractor : NodeKindExtractor {
    override fun id() = "service-layer"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        if (NkxUtil.hasAnyAnn(a, "Service")) {
            return NodeKind.SERVICE
        }
        // Иногда сервисы помечают просто @Component + package ...service
        if (NkxUtil.hasAnyAnn(a, "Component") && pkg.contains(".service")){
            return NodeKind.SERVICE
        }
        // Нейминг
        if (pkg.contains(".service") && NkxUtil.nameEnds(n, "Service")) {
            return NodeKind.SERVICE
        }

        return null
    }
}