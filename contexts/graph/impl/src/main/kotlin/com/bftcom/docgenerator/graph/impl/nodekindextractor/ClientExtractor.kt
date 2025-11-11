package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

@Component
class ClientExtractor : NodeKindExtractor {
    override fun id() = "client-class"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val s = NkxUtil.supers(raw.supertypesRepr)
        val imps = NkxUtil.imps(ctx.imports)

        // Feign
        if (NkxUtil.hasAnyAnn(a, "FeignClient")) {
            return NodeKind.CLIENT
        }
        // gRPC stubs чаще генерятся — эвристика по импортам пакетов grpc
        if (NkxUtil.importsContain(imps, "io.grpc", "net.devh.boot.grpc.client")) {
            return NodeKind.CLIENT
        }
        return null
    }
}
