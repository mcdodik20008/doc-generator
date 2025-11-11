package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

@Component
class SchemaModelExtractor : NodeKindExtractor {
    override fun id() = "schema-model"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(
        base: NodeKind,
        raw: RawType,
        ctx: NodeKindContext,
    ): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val imps = NkxUtil.imps(ctx.imports)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        // Swagger/OpenAPI
        if (NkxUtil.hasAnyAnn(a, "Schema")) {
            return NodeKind.SCHEMA
        }
        if (NkxUtil.importsContain(imps, "io.swagger.v3.oas")) {
            return NodeKind.SCHEMA
        }

        // Avro (эвристика по импортам пакетов avro)
        if (NkxUtil.importsContain(imps, "org.apache.avro", "io.confluent")) {
            return NodeKind.SCHEMA
        }

        if (pkg.contains(".schema")) {
            return NodeKind.SCHEMA
        }
        return null
    }
}
