package com.bftcom.docgenerator.graph.api.declhandler

import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import kotlin.reflect.KClass

interface DeclPlanner<T : RawDecl> {
    val target: KClass<T>
    fun plan(raw: T): List<DeclCmd>
}