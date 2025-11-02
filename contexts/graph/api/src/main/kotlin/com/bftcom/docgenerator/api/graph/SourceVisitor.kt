package com.bftcom.docgenerator.api.graph

import com.bftcom.docgenerator.model.RawUsage

interface SourceVisitor {
    fun onPackage(
        pkgFqn: String,
        filePath: String,
    )

    fun onType(
        kind: com.bftcom.docgenerator.domain.enums.NodeKind,
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

    /** Новый: передаём контекст файла (imports) перед типами/функциями этого файла */
    fun onFileContext(
        pkgFqn: String,
        filePath: String,
        imports: List<String>,
    )

    fun onTypeEx(
        kind: com.bftcom.docgenerator.domain.enums.NodeKind,
        fqn: String,
        pkgFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        supertypesSimple: List<String>,
        sourceCode: String?,
        signature: String?,
        docComment: String?,
        kdocMeta: Map<String, Any?>? = null,
    )

    fun onFieldEx(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        sourceCode: String?,
        docComment: String?,
        kdocMeta: Map<String, Any?>? = null,
    )

    fun onFunctionEx(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        usages: List<RawUsage>,
        sourceCode: String?,
        signature: String?,
        docComment: String?,
        annotations: Set<String>?,
        kdocMeta: Map<String, Any?>? = null,
    )
}
