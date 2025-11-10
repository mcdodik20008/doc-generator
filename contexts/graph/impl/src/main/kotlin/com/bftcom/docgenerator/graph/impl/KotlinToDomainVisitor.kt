package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawPackage
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.fasterxml.jackson.databind.ObjectMapper

class KotlinToDomainVisitor(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
) : SourceVisitor {

    private val packageByFqn = mutableMapOf<String, Node>()      // "foo" -> PACKAGE
    private val typeByFqn = mutableMapOf<String, Node>()         // "foo.A" -> CLASS/INTERFACE/ENUM/RECORD
    private val funcByFqn = mutableMapOf<String, Node>()         // "foo.baz" / "foo.A.bar" -> METHOD
    private val filePkg = mutableMapOf<String, String>()         // filePath -> "foo"
    private val fileImports = mutableMapOf<String, List<String>>()// filePath -> imports

    override fun onDecl(raw: RawDecl) {
        when (raw) {
            is RawFileUnit -> onFileUnit(raw)   // приватный метод в твоём визиторе
            is RawPackage  -> onPackageDecl(raw)
            is RawType     -> onType(raw)
            is RawField    -> onField(raw)
            is RawFunction -> onFunction(raw)
        }
    }

    private fun onPackageDecl(r: RawPackage) {
        val pkg = r.name
        // Кладём/апдейтим package-ноду ровно как ты делал для FileUnit (если нужно — можно вообще no-op)
        packageByFqn.getOrPut(pkg) {
            upsertNode(
                fqn = pkg,
                kind = NodeKind.PACKAGE,
                name = pkg.substringAfterLast('.'),
                packageName = pkg,
                parent = null,
                lang = Lang.kotlin,
                filePath = r.filePath,
                span = r.span?.let { it.start..it.end },
                signature = null,
                sourceCode = r.text,
                docComment = null,
                meta = NodeMeta(source = "package", pkgFqn = pkg),
            )
        }
    }

    // -------------------- FILE UNIT --------------------

    private fun onFileUnit(r: RawFileUnit) {
        // Ровно то, что уже делалось: кешируем pkg/imports и при наличии pkg создаём PACKAGE-ноду
        r.pkgFqn?.let { pkg ->
            filePkg[r.filePath] = pkg
            fileImports[r.filePath] = r.imports
            packageByFqn.getOrPut(pkg) {
                upsertNode(
                    fqn = pkg,
                    kind = NodeKind.PACKAGE,
                    name = pkg.substringAfterLast('.'),
                    packageName = pkg,
                    parent = null,
                    lang = Lang.kotlin,
                    filePath = r.filePath,
                    span = r.span?.let { it.start..it.end },
                    signature = null,
                    sourceCode = r.text,
                    docComment = null,
                    meta = NodeMeta(source = "fileUnit", pkgFqn = pkg),
                )
            }
        } ?: run {
            filePkg[r.filePath] = ""
            fileImports[r.filePath] = r.imports
        }
    }

    // -------------------- TYPE --------------------

    override fun onType(r: RawType) {
        val pkgFqn = r.pkgFqn ?: filePkg[r.filePath].orEmpty()
        val fqn = listOfNotNull(pkgFqn.takeIf { it.isNotBlank() }, r.simpleName).joinToString(".")

        val pkgNode = if (pkgFqn.isNotBlank()) {
            packageByFqn.getOrPut(pkgFqn) {
                val meta = NodeMeta(source = "type:pkgAuto", pkgFqn = pkgFqn)
                upsertNode(
                    fqn = pkgFqn,
                    kind = NodeKind.PACKAGE,
                    name = pkgFqn.substringAfterLast('.'),
                    packageName = pkgFqn,
                    parent = null,
                    lang = Lang.kotlin,
                    filePath = r.filePath,
                    span = null,
                    signature = null,
                    sourceCode = null,
                    docComment = null,
                    meta = meta,
                )
            }
        } else null

        val kind = when (r.kindRepr) {
            "interface" -> NodeKind.INTERFACE
            "enum"      -> NodeKind.ENUM
            "record"    -> NodeKind.RECORD
            "object"    -> NodeKind.CLASS  // оставляем как CLASS
            else        -> NodeKind.CLASS
        }

        val meta = NodeMeta(
            source = "type",
            pkgFqn = pkgFqn.ifBlank { null },
            supertypesSimple = r.supertypesRepr,
            imports = fileImports[r.filePath],
            kdoc = r.attributes["kdoc"]?.let { null } ?: null, // не выдумываем, если нет
            annotations = r.annotationsRepr,
        )

        val spanRange = r.span?.let { it.start..it.end }
        val node = upsertNode(
            fqn = fqn,
            kind = kind,
            name = r.simpleName,
            packageName = pkgFqn.ifBlank { null },
            parent = pkgNode,
            lang = Lang.kotlin,
            filePath = r.filePath,
            span = spanRange,
            signature = r.attributes["signature"] as? String,
            sourceCode = r.text,
            docComment = null, // сырой док не нормализуем
            meta = meta,
        )
        typeByFqn[fqn] = node
    }

    // -------------------- FIELD --------------------

    override fun onField(r: RawField) {
        val pkg = r.pkgFqn ?: filePkg[r.filePath]
        val parent = r.ownerFqn?.let { typeByFqn[it] } ?: pkg?.let { packageByFqn[it] }

        val meta = NodeMeta(
            source = "field",
            pkgFqn = pkg,
            ownerFqn = r.ownerFqn,
            kdoc = r.kdoc?.let { KDocMeta(summary = it) },
            annotations = r.annotationsRepr,
        )

        val spanRange = r.span?.let { it.start..it.end }
        upsertNode(
            fqn = listOfNotNull(r.ownerFqn, r.name).joinToString("."),
            kind = NodeKind.FIELD,
            name = r.name,
            packageName = pkg,
            parent = parent,
            lang = Lang.kotlin,
            filePath = r.filePath,
            span = spanRange,
            signature = null,
            sourceCode = r.text,
            docComment = r.kdoc,
            meta = meta,
        )
    }

    // -------------------- FUNCTION --------------------

    override fun onFunction(r: RawFunction) {
        val pkgFqn = r.pkgFqn ?: filePkg[r.filePath]
        val fqn = when {
            !r.ownerFqn.isNullOrBlank() -> "${r.ownerFqn}.${r.name}"
            !pkgFqn.isNullOrBlank()     -> "$pkgFqn.${r.name}"
            else                        -> r.name
        }

        val parent = when {
            !r.ownerFqn.isNullOrBlank() -> typeByFqn[r.ownerFqn]
            !pkgFqn.isNullOrBlank()     -> packageByFqn[pkgFqn!!]
            else                        -> null
        }

        val sig = r.signatureRepr ?: buildString {
            append(r.name).append('(').append(r.paramNames.joinToString(",")).append(')')
        }

        val meta = NodeMeta(
            source = "function",
            pkgFqn = pkgFqn,
            ownerFqn = r.ownerFqn,
            params = r.paramNames,
            rawUsages = r.rawUsages,          // без нормализации/фильтрации
            annotations = r.annotationsRepr.toList(),
            imports = fileImports[r.filePath],
            throwsTypes = r.throwsRepr,
            kdoc = r.kdoc?.let { KDocMeta(summary = it) },
        )

        val spanRange = r.span?.let { it.start..it.end }
        val node = upsertNode(
            fqn = fqn,
            kind = NodeKind.METHOD,           // без эвристик
            name = r.name,
            packageName = pkgFqn,
            parent = parent,
            lang = Lang.kotlin,
            filePath = r.filePath,
            span = spanRange,
            signature = sig,
            sourceCode = r.text,
            docComment = r.kdoc,
            meta = meta,
        )
        funcByFqn[fqn] = node
    }

    // -------------------- UPSERT --------------------

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
        meta: NodeMeta,
    ): Node {
        val existing = nodeRepo.findByApplicationIdAndFqn(application.id!!, fqn)
        val metaMap = toMetaMap(meta)

        var lineStart: Int? = span?.first
        var lineEnd: Int? = span?.last
        if (sourceCode?.isNotEmpty() == true && lineStart != null) {
            lineEnd = lineStart + countLinesNormalized(sourceCode) - 1
        }

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
                    lineStart = lineStart,
                    lineEnd = lineEnd,
                    sourceCode = sourceCode,
                    docComment = docComment,
                    signature = signature,
                    codeHash = null,
                    meta = metaMap,
                ),
            )
        } else {
            var changed = false

            fun <T> setIfChanged(curr: T, new: T, apply: (T) -> Unit) {
                if (curr != new) { apply(new); changed = true }
            }

            setIfChanged(existing.name, name) { existing.name = it }
            setIfChanged(existing.packageName, packageName) { existing.packageName = it }
            setIfChanged(existing.kind, kind) { existing.kind = it }
            setIfChanged(existing.lang, lang) { existing.lang = it }
            setIfChanged(existing.parent?.id, parent?.id) { existing.parent = parent }
            setIfChanged(existing.filePath, filePath) { existing.filePath = it }
            setIfChanged(existing.lineStart, lineStart) { existing.lineStart = it }
            setIfChanged(existing.lineEnd, lineEnd) { existing.lineEnd = it }
            setIfChanged(existing.sourceCode, sourceCode) { existing.sourceCode = it }
            setIfChanged(existing.docComment, docComment) { existing.docComment = it }
            setIfChanged(existing.signature, signature) { existing.signature = it }

            @Suppress("UNCHECKED_CAST")
            val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
            val merged = (currentMeta + metaMap).cleaned()
            setIfChanged(existing.meta, merged) { existing.meta = it }

            if (changed) nodeRepo.save(existing) else existing
        }
    }

    // -------------------- УТИЛИТЫ --------------------

    private fun countLinesNormalized(src: String): Int {
        if (src.isEmpty()) return 0
        val s = src.replace("\r\n", "\n")
        var count = 1
        for (ch in s) if (ch == '\n') count++
        return count
    }

    private fun Map<String, Any?>.cleaned(): Map<String, Any> =
        entries.asSequence()
            .filter { (_, v) ->
                v != null && when (v) {
                    is Collection<*> -> v.isNotEmpty()
                    is Map<*, *>     -> v.isNotEmpty()
                    else             -> true
                }
            }
            .associate { (k, v) ->
                val vv = when (v) {
                    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (v as Map<String, Any?>).cleaned()
                    else -> v
                }
                k to vv
            } as Map<String, Any>

    private fun toMetaMap(meta: NodeMeta): Map<String, Any> =
        objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>
}
