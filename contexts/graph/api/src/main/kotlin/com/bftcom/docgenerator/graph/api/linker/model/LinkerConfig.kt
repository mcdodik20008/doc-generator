package com.bftcom.docgenerator.graph.api.linker.model

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.graph.api.linker.detector.EdgeDetector
import kotlin.reflect.KClass

interface LinkerConfig {
    fun isEnabled(detector: KClass<out EdgeDetector>): Boolean

    fun getPriority(kind: EdgeKind): Int
}
