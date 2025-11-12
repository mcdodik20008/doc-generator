package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declplanner.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class FunctionPlanner : BaseDeclPlanner<RawFunction>(RawFunction::class as KClass<RawFunction>) {
    override fun plan(raw: RawFunction): List<DeclCmd> =
        buildList {
            raw.pkgFqn?.let { add(EnsurePackageCmd(it, raw.filePath)) }
            add(UpsertFunctionCmd(raw))
        }
}
