package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.graph.api.CommandExecutor
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.api.declhandler.DeclPlanner
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import kotlin.reflect.KClass

class KotlinToDomainVisitor(
    private val exec: CommandExecutor,
    planners: List<DeclPlanner<*>>,
) : SourceVisitor {
    private val registry: Map<KClass<out RawDecl>, DeclPlanner<out RawDecl>> =
        planners.associateBy { it.target }

    override fun onDecl(raw: RawDecl) {
        @Suppress("UNCHECKED_CAST")
        val h = registry[raw::class] as? DeclPlanner<RawDecl> ?: return
        h.plan(raw).forEach(exec::execute)
    }
}
