package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declhandler.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declhandler.DeclCmd
import com.bftcom.docgenerator.graph.api.declhandler.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawPackage
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class PackagePlanner : BaseDeclPlanner<RawPackage>(RawPackage::class as KClass<RawPackage>) {
    override fun plan(raw: RawPackage): List<DeclCmd> =
        listOf(EnsurePackageCmd(raw.name, raw.filePath, raw.span?.start ?: 0, raw.span?.end ?: 1, raw.text))
}