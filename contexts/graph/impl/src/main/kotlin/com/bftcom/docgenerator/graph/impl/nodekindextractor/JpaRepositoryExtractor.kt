package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class JpaRepositoryExtractor : NodeKindExtractor {
    override fun id() = "jpa-repository"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val s = NkxUtil.supers(raw.supertypesRepr)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        if (NkxUtil.hasAnyAnn(a, "Repository")) {
            return NodeKind.DB_QUERY
        }
        if (NkxUtil.superContains(s, "JpaRepository", "CrudRepository", "PagingAndSortingRepository")) return NodeKind.DB_QUERY
        if (pkg.contains(".repository") || NkxUtil.nameEnds(n, "Repository", "Dao")) {
            return NodeKind.DB_QUERY
        }

        return null
    }
}