package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

@Component
class MyBatisMapperExtractor : NodeKindExtractor {
    override fun id() = "mybatis-mapper"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)
        if (NkxUtil.hasAnyAnn(a, "Mapper")) {
            return NodeKind.MAPPER
        }
        if (pkg.contains(".mapper") || NkxUtil.nameEnds(n, "Mapper")) {
            return NodeKind.MAPPER
        }
        return null
    }
}
