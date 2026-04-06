package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.toLang
import org.springframework.stereotype.Component

// Extractor priority (Spring @Order):
//   0  — TestClassExtractor (tests always win)
//  10  — all other extractors (first-match-wins among them)
//
// Within each extractor, signal strength:
//   1. Annotations (@Service, @Mapper, @Repository, etc.) — strongest
//   2. Supertypes (extends Exception, JavaMigration, etc.) — strong
//   3. Imports (junit, kafka, grpc, etc.) — medium
//   4. Package name heuristics (.service, .mapper, etc.) — weakest
//   5. Class name suffix (ends with Service, Mapper, etc.) — weakest
@Component
class NodeKindRefinerImpl(
    private val extractors: List<NodeKindExtractor>,
) : NodeKindRefiner {
    override fun forType(
        base: NodeKind,
        raw: RawType,
        fileUnit: RawFileUnit?,
    ): NodeKind {
        if (extractors.isEmpty()) return base
        val ctx = buildCtx(raw, fileUnit)
        return extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.refineType(base, raw, ctx) }
            .firstOrNull() ?: base
    }

    override fun forFunction(
        base: NodeKind,
        raw: RawFunction,
        fileUnit: RawFileUnit?,
    ): NodeKind {
        if (extractors.isEmpty()) return base
        val ctx = buildCtx(raw, fileUnit)
        return extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.refineFunction(base, raw, ctx) }
            .firstOrNull() ?: base
    }

    override fun forField(
        base: NodeKind,
        raw: RawField,
        fileUnit: RawFileUnit?,
    ): NodeKind {
        if (extractors.isEmpty()) return base
        val ctx = buildCtx(raw, fileUnit)
        return extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.refineField(base, raw, ctx) }
            .firstOrNull() ?: base
    }

    private fun buildCtx(
        raw: RawDecl,
        fileUnit: RawFileUnit?,
    ): NodeKindContext = NodeKindContext(lang = raw.lang.toLang(), file = fileUnit, imports = fileUnit?.imports)
}
