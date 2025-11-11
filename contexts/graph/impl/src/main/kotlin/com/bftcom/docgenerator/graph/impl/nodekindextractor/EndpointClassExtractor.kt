package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class EndpointClassExtractor : NodeKindExtractor {
    override fun id() = "endpoint-class"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        if (NkxUtil.hasAnyAnn(a, "RestController", "Controller")) {
            return NodeKind.ENDPOINT
        }
        // gRPC (yidongnan/grpc-spring-boot-starter)
        if (NkxUtil.hasAnyAnn(a, "GrpcService")) {
            return NodeKind.ENDPOINT
        }
        return null
    }
}