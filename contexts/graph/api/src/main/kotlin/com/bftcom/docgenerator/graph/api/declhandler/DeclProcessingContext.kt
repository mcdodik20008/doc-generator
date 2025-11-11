package com.bftcom.docgenerator.graph.api.declhandler

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType

/** Минимальный API для хендлеров. */
interface DeclProcessingContext {
    val lang: Lang

    fun getFilePkg(path: String): String?
    fun getFileImports(path: String): List<String>?
    fun rememberFileUnit(unit: RawFileUnit)
    fun getFileUnit(path: String): RawFileUnit?

    fun getOrPutPackage(pkgFqn: String, builder: () -> Node): Node
    fun rememberTypeNode(fqn: String, node: Node)
    fun getTypeNode(fqn: String): Node?
    fun rememberFuncNode(fqn: String, node: Node)

    fun upsertNode(
        fqn: String,
        kind: NodeKind,
        name: String?,
        packageName: String?,
        parent: Node?,
        lang: Lang,
        filePath: String?,
        span: IntRange?,
        signature: String?,
        sourceCode: String?,
        docComment: String?,
        meta: NodeMeta,
    ): Node

    fun refineKindForType(base: NodeKind, raw: RawType, fileUnit: RawFileUnit?): NodeKind
    fun refineKindForFunction(base: NodeKind, raw: RawFunction, fileUnit: RawFileUnit?): NodeKind
    fun refineKindForField(base: NodeKind, raw: RawField, fileUnit: RawFileUnit?): NodeKind
}