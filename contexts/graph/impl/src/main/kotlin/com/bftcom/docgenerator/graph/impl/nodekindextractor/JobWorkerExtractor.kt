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
class JobWorkerExtractor : NodeKindExtractor {
    override fun id() = "job-worker"

    override fun supports(lang: Lang) = (lang == Lang.kotlin || lang == Lang.java)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val s = NkxUtil.supers(raw.supertypesRepr)
        val n = NkxUtil.name(raw.simpleName)
        val imps = NkxUtil.imps(ctx.imports)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        // Quartz / Spring Batch
        if (NkxUtil.superContains(s, "Job", "Tasklet")) {
            return NodeKind.JOB
        }
        if (NkxUtil.importsContain(imps, "org.springframework.batch", "org.quartz")) {
            return NodeKind.JOB
        }

        // @Scheduled обычно на методах, поэтому — эвристика по импорту + нейминг
        if (NkxUtil.importsContain(imps, "org.springframework.scheduling.annotation.scheduled") &&
            NkxUtil.nameContains(n, "Scheduler", "Job", "Worker", "Task")
        ) {
            return NodeKind.JOB
        }

        if (NkxUtil.pkgHasSegment(pkg, "job") || NkxUtil.nameEnds(n, "Job", "Worker", "Scheduler")) {
            return NodeKind.JOB
        }

        return null
    }

    override fun refineFunction(
        base: NodeKind,
        raw: RawFunction,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr.toList())
        if (NkxUtil.hasAnyAnn(a, "Scheduled")) return NodeKind.JOB
        return null
    }
}
