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

@Order(0)
@Component
class TestClassExtractor : NodeKindExtractor {
    override fun id() = "test-class"

    override fun supports(lang: Lang) = (lang == Lang.kotlin || lang == Lang.java)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)
        val imps = NkxUtil.imps(ctx.imports)
        if (NkxUtil.nameEnds(n, "Test", "IT", "Spec")) {
            return NodeKind.TEST
        }
        if (NkxUtil.pkgHasSegment(pkg, "test")) {
            return NodeKind.TEST
        }
        if (NkxUtil.importsContain(imps, "org.junit", "junit.jupiter", "kotest", "mockk")) {
            return NodeKind.TEST
        }
        return null
    }

    override fun refineFunction(
        base: NodeKind,
        raw: RawFunction,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr.toList())
        if (NkxUtil.hasAnyAnn(a, "Test", "ParameterizedTest", "RepeatedTest")) return NodeKind.TEST
        val imps = NkxUtil.imps(ctx.imports)
        if (NkxUtil.importsContain(imps, "org.junit", "junit.jupiter", "kotest")) return NodeKind.TEST
        return null
    }
}
