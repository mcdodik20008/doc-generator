package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class TestClassExtractor : NodeKindExtractor {
    override fun id() = "test-class"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)
        val imps = NkxUtil.imps(ctx.imports)
        if (NkxUtil.nameEnds(n, "Test", "IT", "Spec")) {
            return NodeKind.TEST
        }
        if (pkg.contains(".test")) {
            return NodeKind.TEST
        }
        if (NkxUtil.importsContain(imps, "org.junit", "junit.jupiter", "kotest", "mockk")) {
            return NodeKind.TEST
        }
        return null
    }
}