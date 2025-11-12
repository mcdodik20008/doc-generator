package com.bftcom.docgenerator.graph.api.declplanner

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType

sealed interface DeclCmd

data class RememberFileUnitCmd(
    val unit: RawFileUnit,
) : DeclCmd

data class EnsurePackageCmd(
    val pkgFqn: String,
    val filePath: String,
    val spanStart: Int = 0,
    val spanEnd: Int = 1,
    val sourceText: String? = null,
) : DeclCmd

data class UpsertTypeCmd(
    val raw: RawType,
    val baseKind: NodeKind,
) : DeclCmd

data class UpsertFieldCmd(
    val raw: RawField,
) : DeclCmd

data class UpsertFunctionCmd(
    val raw: RawFunction,
) : DeclCmd
