package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.enums.NodeKind

interface SourceVisitor {
    fun onPackage(
        pkgFqn: String,
        filePath: String,
    )

    fun onType(
        kind: NodeKind, // CLASS/INTERFACE/ENUM/RECORD
        fqn: String,
        pkgFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        supertypesSimple: List<String>,
    )

    fun onField(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
    )

    fun onFunction(
        ownerFqn: String?, // null => top-level function
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        callsSimple: List<String>,
    )
}
