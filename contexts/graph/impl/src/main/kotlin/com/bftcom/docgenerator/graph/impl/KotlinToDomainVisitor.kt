package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import com.bftcom.docgenerator.graph.api.declhandler.DeclPlanner
import kotlin.reflect.KClass

class KotlinToDomainVisitor(
    private val exec: Executor,
    planners: List<DeclPlanner<*>>,
) : SourceVisitor {

    // Полиморфный реестр: RawDecl::class -> handler
    private val registry: Map<KClass<out RawDecl>, DeclPlanner<out RawDecl>> =
        planners.associateBy { it.target }

    // Единственный исполнитель с состоянием и доступом к БД

    override fun onDecl(raw: RawDecl) {
        @Suppress("UNCHECKED_CAST")
        val h = registry[raw::class] as? DeclPlanner<RawDecl> ?: return
        h.plan(raw).forEach(exec::execute)
    }

    // --- Миниатюрный исполнитель (вынесли всю «тяжёлую» логику из визитора) ---

}
