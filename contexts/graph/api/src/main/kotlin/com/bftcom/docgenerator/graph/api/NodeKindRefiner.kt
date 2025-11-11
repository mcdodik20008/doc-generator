package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType

interface NodeKindRefiner {
    fun forType(base: NodeKind, raw: RawType, fileUnit: RawFileUnit?): NodeKind
    fun forFunction(base: NodeKind, raw: RawFunction, fileUnit: RawFileUnit?): NodeKind
    fun forField(base: NodeKind, raw: RawField, fileUnit: RawFileUnit?): NodeKind
}