package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.enums.NodeKind

interface RichSourceVisitor : SourceVisitor {
    fun onTypeEx(
        kind: NodeKind,
        fqn: String,
        pkgFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        supertypesSimple: List<String>,
        sourceCode: String?,
        signature: String?,
        docComment: String?,
    )

    fun onFieldEx(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        sourceCode: String?,
        docComment: String?,
    )

    fun onFunctionEx(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        callsSimple: List<String>,
        sourceCode: String?,
        signature: String?,
        docComment: String?,
    )
}
