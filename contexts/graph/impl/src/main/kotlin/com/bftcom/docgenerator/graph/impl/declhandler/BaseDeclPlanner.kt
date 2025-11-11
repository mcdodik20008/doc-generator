package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.graph.api.declhandler.DeclPlanner
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import kotlin.reflect.KClass

abstract class BaseDeclPlanner<T : RawDecl>(
    final override val target: KClass<T>
) : DeclPlanner<T> {

    protected fun countLinesNormalized(src: String?): Int {
        if (src.isNullOrEmpty()) return 0
        var c = 1
        for (ch in src.replace("\r\n", "\n")) if (ch == '\n') c++
        return c
    }

    protected fun span(start: Int?, end: Int?): IntRange? =
        if (start != null && end != null) start..end else null

    protected fun fqnOf(vararg parts: String?): String =
        parts.filter { !it.isNullOrBlank() }.joinToString(".")
}