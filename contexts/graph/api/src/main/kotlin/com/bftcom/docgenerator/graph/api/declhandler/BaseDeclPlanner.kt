package com.bftcom.docgenerator.graph.api.declhandler

import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import kotlin.reflect.KClass

abstract class BaseDeclPlanner<T : RawDecl>(
    final override val target: KClass<T>,
) : DeclPlanner<T>
