package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.RichSourceVisitor
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository

/**
 * Kotlin → доменная модель графа (Node/Edge).
 * Поддерживает «расширенные» колбэки (RichSourceVisitor) с sourceCode/signature/docComment.
 */
class KotlinToDomainVisitor(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    // ToDo: ToSpringProps
    private val noise: Set<String> = setOf("listOf","map","of","timer","start","stop")
) : RichSourceVisitor {

    // Кэши для быстрого доступа
    private val packageByFqn = mutableMapOf<String, Node>()   // "foo" -> PACKAGE
    private val typeByFqn    = mutableMapOf<String, Node>()   // "foo.A" -> CLASS/INTERFACE/ENUM/...
    private val funcByFqn    = mutableMapOf<String, Node>()   // "foo.baz" / "foo.A.bar" -> METHOD
    private val filePkg      = mutableMapOf<String, String>() // filePath -> "foo"

    // -------------------- PACKAGE --------------------

    override fun onPackage(pkgFqn: String, filePath: String) {
        filePkg[filePath] = pkgFqn
        packageByFqn.getOrPut(pkgFqn) {
            upsertNode(
                fqn = pkgFqn,
                kind = NodeKind.PACKAGE,
                name = pkgFqn.substringAfterLast('.'),
                packageName = pkgFqn,
                parent = null,
                lang = Lang.kotlin,
                filePath = filePath,
                span = 1..1,
                signature = null,
                sourceCode = null,
                docComment = null,
                extraMeta = mapOf("pkgFqn" to pkgFqn, "source" to "onPackage"),
            )
        }
    }

    // -------------------- TYPE (basic) --------------------

    override fun onType(
        kind: NodeKind,
        fqn: String,
        pkgFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        supertypesSimple: List<String>,
    ) = onTypeEx(kind, fqn, pkgFqn, name, filePath, spanLines, supertypesSimple, null, null, null)

    // -------------------- TYPE (extended) --------------------

    override fun onTypeEx(
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
    ) {
        val pkgNode = packageByFqn.getOrPut(pkgFqn) {
            upsertNode(
                fqn = pkgFqn,
                kind = NodeKind.PACKAGE,
                name = pkgFqn.substringAfterLast('.'),
                packageName = pkgFqn,
                parent = null,
                lang = Lang.kotlin,
                filePath = filePath,
                span = 1..1,
                signature = null,
                sourceCode = null,
                docComment = null,
                extraMeta = mapOf("pkgFqn" to pkgFqn, "source" to "onType:pkgAuto"),
            )
        }

        val typeNode = upsertNode(
            fqn = fqn,
            kind = kind,
            name = name,
            packageName = pkgFqn,
            parent = pkgNode,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = signature,
            sourceCode = sourceCode,
            docComment = docComment,
            extraMeta = mapOf("pkgFqn" to pkgFqn, "supertypesSimple" to supertypesSimple, "source" to "onType"),
        )
        typeByFqn[fqn] = typeNode
    }

    // -------------------- FIELD (basic) --------------------

    override fun onField(ownerFqn: String, name: String, filePath: String, spanLines: IntRange) =
        onFieldEx(ownerFqn, name, filePath, spanLines, null, null)

    // -------------------- FIELD (extended) --------------------

    override fun onFieldEx(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        sourceCode: String?,
        docComment: String?,
    ) {
        val pkg = filePkg[filePath]
        val parent = typeByFqn[ownerFqn] ?: packageByFqn[pkg]
        upsertNode(
            fqn = "$ownerFqn.$name",
            kind = NodeKind.FIELD,
            name = name,
            packageName = pkg,
            parent = parent,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = null,
            sourceCode = sourceCode,
            docComment = docComment,
            extraMeta = mapOf("ownerFqn" to ownerFqn, "pkgFqn" to pkg, "source" to "onField"),
        )
    }

    override fun onFunction(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        callsSimple: List<String>,
    ) = onFunctionEx(ownerFqn, name, paramNames, filePath, spanLines, callsSimple, null, null, null)

    override fun onFunctionEx(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        callsSimple: List<String>,
        sourceCode: String?,
        signature: String?,
        docComment: String?,
    ) {
        val pkgFqn = filePkg[filePath]
        val fqn = when {
            !ownerFqn.isNullOrBlank() -> "$ownerFqn.$name"
            !pkgFqn.isNullOrBlank()   -> "$pkgFqn.$name"
            else -> name
        }

        val sig = signature ?: buildString {
            append(name); append('('); append(paramNames.joinToString(",")); append(')')
        }
        val callsFiltered = callsSimple.filterNot { it in noise }

        val fnNode = upsertNode(
            fqn = fqn,
            kind = NodeKind.METHOD,
            name = name,
            packageName = pkgFqn,
            parent = when {
                ownerFqn != null -> typeByFqn[ownerFqn]
                pkgFqn != null   -> packageByFqn[pkgFqn]
                else             -> null
            },
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = sig,
            sourceCode = sourceCode,
            docComment = docComment,
            extraMeta = mapOf(
                "pkgFqn" to pkgFqn,
                "ownerFqn" to ownerFqn,
                "params" to paramNames,
                "callsSimple" to callsFiltered,
                "source" to "onFunction",
            ),
        )
        funcByFqn[fqn] = fnNode

        // временные связи по простым именам до резолва FQN
        for (token in callsSimple) {
            val candidates = when {
                '.' in token -> {
                    val (lhs, rhs) = token.split('.', limit = 2)
                    if (lhs.contains('.')) listOf("$lhs.$rhs")
                    else listOfNotNull(pkgFqn?.let { "$it.$lhs.$rhs" })
                }
                else -> buildList {
                    if (!pkgFqn.isNullOrBlank()) add("$pkgFqn.$token")
                    if (!ownerFqn.isNullOrBlank()) add("$ownerFqn.$token")
                    add(token)
                }
            }

            val dst = candidates
                .asSequence()
                .mapNotNull { cfqn -> funcByFqn[cfqn] ?: nodeRepo.findByApplicationIdAndFqn(application.id!!, cfqn) }
                .firstOrNull()
                ?: continue

            runCatching { edgeRepo.save(Edge(src = fnNode, dst = dst, kind = EdgeKind.CALLS)) }
        }
    }

    private fun upsertNode(
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
        extraMeta: Map<String, Any?>,
    ): Node {
        val existing = nodeRepo.findByApplicationIdAndFqn(application.id!!, fqn)
        val newMeta = extraMeta.filterValues { it != null } as Map<String, Any>

        return if (existing == null) {
            nodeRepo.save(
                Node(
                    id = null,
                    application = application,
                    fqn = fqn,
                    name = name,
                    packageName = packageName,
                    kind = kind,
                    lang = lang,
                    parent = parent,
                    filePath = filePath,
                    lineStart = span?.first,
                    lineEnd = span?.last,
                    sourceCode = sourceCode,
                    docComment = docComment,
                    signature = signature,
                    codeHash = null,
                    meta = newMeta,
                )
            )
        } else {
            var changed = false
            if (existing.name != name) { existing.name = name; changed = true }
            if (existing.packageName != packageName) { existing.packageName = packageName; changed = true }
            if (existing.kind != kind) { existing.kind = kind; changed = true }
            if (existing.lang != lang) { existing.lang = lang; changed = true }
            if ((existing.parent?.id) != (parent?.id)) { existing.parent = parent; changed = true }
            if (existing.filePath != filePath) { existing.filePath = filePath; changed = true }
            if (existing.lineStart != span?.first) { existing.lineStart = span?.first; changed = true }
            if (existing.lineEnd != span?.last) { existing.lineEnd = span?.last; changed = true }
            if (existing.sourceCode != sourceCode) { existing.sourceCode = sourceCode; changed = true }
            if (existing.docComment != docComment) { existing.docComment = docComment; changed = true }
            if (existing.signature != signature) { existing.signature = signature; changed = true }

            val mergedMeta = (existing.meta + newMeta)
            if (existing.meta != mergedMeta) { existing.meta = mergedMeta; changed = true }

            if (changed) nodeRepo.save(existing) else existing
        }
    }
}
