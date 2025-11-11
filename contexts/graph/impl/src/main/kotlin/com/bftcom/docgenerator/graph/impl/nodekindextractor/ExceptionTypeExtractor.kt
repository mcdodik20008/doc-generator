package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

@Component
class ExceptionTypeExtractor : NodeKindExtractor {
    override fun id() = "exception-type"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val s = NkxUtil.supers(raw.supertypesRepr)
        val n = NkxUtil.name(raw.simpleName)
        if (NkxUtil.superContains(s, "Throwable", "Exception", "RuntimeException")) {
            return NodeKind.EXCEPTION
        }
        if (NkxUtil.nameEnds(n, "Exception", "Error")) {
            return NodeKind.EXCEPTION
        }
        return null
    }
}
