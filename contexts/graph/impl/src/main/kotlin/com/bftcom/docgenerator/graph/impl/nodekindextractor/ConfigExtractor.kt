package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import org.springframework.stereotype.Component

@Component
class ConfigExtractor : NodeKindExtractor {
    override fun id() = "config-class"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? {
        val a = NkxUtil.anns(raw.annotationsRepr)
        val n = NkxUtil.name(raw.simpleName)
        val pkg = NkxUtil.pkg(raw.pkgFqn)

        // @ConfigurationProperties — явный конфиг-класс свойств
        if (NkxUtil.hasAnyAnn(a, "ConfigurationProperties")) {
            return NodeKind.CONFIG
        }
        // @Configuration — класс конфигурации (источник бинов)
        if (NkxUtil.hasAnyAnn(a, "Configuration")) {
            return NodeKind.CONFIG
        }
        // package-эвристики/нейминг
        if (pkg.contains(".config") || NkxUtil.nameEnds(n, "Config", "Configuration", "Properties")){
            return NodeKind.CONFIG
        }

        return null
    }
}