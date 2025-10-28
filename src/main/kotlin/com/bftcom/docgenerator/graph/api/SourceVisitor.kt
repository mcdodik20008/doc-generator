package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.model.RawUsage

interface SourceVisitor {
    fun onPackage(
        pkgFqn: String,
        filePath: String,
    )

    fun onType(
        kind: NodeKind,
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
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        usages: List<RawUsage>,
    )
}
