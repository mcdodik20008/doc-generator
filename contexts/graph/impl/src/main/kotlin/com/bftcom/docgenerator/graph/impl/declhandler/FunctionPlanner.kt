package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declhandler.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declhandler.DeclCmd
import com.bftcom.docgenerator.graph.api.declhandler.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declhandler.UpsertFunctionCmd
import kotlin.reflect.KClass
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import org.springframework.stereotype.Component

@Component
class FunctionPlanner : BaseDeclPlanner<RawFunction>(RawFunction::class as KClass<RawFunction>) {
    override fun plan(raw: RawFunction): List<DeclCmd> = buildList {
        raw.pkgFqn?.let { add(EnsurePackageCmd(it, raw.filePath)) }
        add(UpsertFunctionCmd(raw))
    }
}