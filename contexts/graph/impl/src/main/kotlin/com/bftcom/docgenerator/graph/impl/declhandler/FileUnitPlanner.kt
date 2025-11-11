package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declhandler.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declhandler.DeclCmd
import com.bftcom.docgenerator.graph.api.declhandler.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declhandler.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class FileUnitPlanner : BaseDeclPlanner<RawFileUnit>(RawFileUnit::class as KClass<RawFileUnit>) {
    override fun plan(raw: RawFileUnit): List<DeclCmd> =
        buildList {
            add(RememberFileUnitCmd(raw))
            raw.pkgFqn?.let { pkg ->
                add(EnsurePackageCmd(pkg, raw.filePath, raw.span?.start ?: 0, raw.span?.end ?: 1, raw.text))
            }
        }
}
