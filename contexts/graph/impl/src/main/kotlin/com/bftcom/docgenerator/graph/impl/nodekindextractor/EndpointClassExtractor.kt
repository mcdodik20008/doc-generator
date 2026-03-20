package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(10)
@Component
class EndpointClassExtractor : NodeKindExtractor {
    override fun id() = "endpoint-class"

    override fun supports(lang: Lang) = (lang == Lang.kotlin || lang == Lang.java)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        if (NkxUtil.hasAnyAnn(a, "RestController", "Controller")) {
            return NodeKind.ENDPOINT
        }
        // gRPC (yidongnan/grpc-spring-boot-starter)
        if (NkxUtil.hasAnyAnn(a, "GrpcService")) {
            return NodeKind.ENDPOINT
        }
        // Feign client interfaces (consumers of HTTP endpoints)
        if (NkxUtil.hasAnyAnn(a, "FeignClient")) {
            return NodeKind.ENDPOINT
        }
        return null
    }

    override fun refineFunction(
        base: NodeKind,
        raw: RawFunction,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr.toList())
        if (NkxUtil.hasAnyAnn(
                a, "GetMapping", "PostMapping", "PutMapping",
                "DeleteMapping", "PatchMapping", "RequestMapping",
            )
        ) return NodeKind.ENDPOINT
        return null
    }
}
