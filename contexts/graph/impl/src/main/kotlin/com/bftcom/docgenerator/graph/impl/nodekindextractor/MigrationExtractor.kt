package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class MigrationExtractor : NodeKindExtractor {
    override fun id() = "migration-class"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val s = NkxUtil.supers(raw.supertypesRepr)
        val imps = NkxUtil.imps(ctx.imports)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        // Flyway JavaMigration
        if (NkxUtil.superContains(s, "JavaMigration")) {
            return NodeKind.MIGRATION
        }
        if (NkxUtil.importsContain(imps, "org.flywaydb.core.api.migration")) {
            return NodeKind.MIGRATION
        }

        // эвристики
        if (pkg.contains(".migration") || NkxUtil.nameEnds(n, "Migration")) {
            return NodeKind.MIGRATION
        }

        return null
    }
}
