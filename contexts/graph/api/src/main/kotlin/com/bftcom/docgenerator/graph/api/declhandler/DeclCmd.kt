package com.bftcom.docgenerator.graph.api.declhandler

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.*

sealed interface DeclCmd

data class RememberFileUnitCmd(val unit: RawFileUnit) : DeclCmd
data class EnsurePackageCmd(
    val pkgFqn: String,
    val filePath: String,
    val spanStart: Int = 0,
    val spanEnd: Int = 1,
    val sourceText: String? = null
) : DeclCmd

data class UpsertTypeCmd(val raw: RawType, val baseKind: NodeKind) : DeclCmd
data class UpsertFieldCmd(val raw: RawField) : DeclCmd
data class UpsertFunctionCmd(val raw: RawFunction) : DeclCmd