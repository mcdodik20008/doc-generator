package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declplanner.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class FieldPlanner : BaseDeclPlanner<RawField>(RawField::class as KClass<RawField>) {
    override fun plan(raw: RawField): List<DeclCmd> =
        buildList {
            raw.pkgFqn?.let { add(EnsurePackageCmd(it, raw.filePath)) }
            add(UpsertFieldCmd(raw))
        }
}
