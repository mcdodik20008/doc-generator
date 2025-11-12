package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

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
        val ctx = NodeKindContext(lang = Lang.kotlin, file = fileUnit, imports = fileUnit?.imports)
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
        val ctx = NodeKindContext(lang = Lang.kotlin, file = fileUnit, imports = fileUnit?.imports)
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
        val ctx = NodeKindContext(lang = Lang.kotlin, file = fileUnit, imports = fileUnit?.imports)
        return extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.refineField(base, raw, ctx) }
            .firstOrNull() ?: base
    }
}
