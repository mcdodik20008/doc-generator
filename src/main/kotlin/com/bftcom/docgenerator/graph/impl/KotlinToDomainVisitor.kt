package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.RichSourceVisitor
import com.bftcom.docgenerator.graph.model.RawUsage
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * [Фаза 1] Kotlin → доменная модель графа (Node).
 * Создаёт ноды и складывает строго типизированные метаданные (NodeMeta) в JSONB.
 * Рёбра не создаёт (линковка — Фаза 2).
 */
class KotlinToDomainVisitor(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository, // оставлен для будущего расширения
    private val objectMapper: ObjectMapper,
    // ToDo: вынести в пропсы
    private val noise: Set<String> = setOf("listOf", "map", "of", "timer", "start", "stop"),
) : RichSourceVisitor {

    // -------------------- КЭШИ --------------------
    private val packageByFqn = mutableMapOf<String, Node>()            // "foo" -> PACKAGE
    private val typeByFqn = mutableMapOf<String, Node>()               // "foo.A" -> CLASS/INTERFACE/ENUM/...
    private val funcByFqn = mutableMapOf<String, Node>()               // "foo.baz" / "foo.A.bar" -> METHOD
    private val filePkg = mutableMapOf<String, String>()               // filePath -> "foo"
    private val fileImports = mutableMapOf<String, List<String>>()     // filePath -> imports

    // -------------------- PACKAGE --------------------

    override fun onPackage(pkgFqn: String, filePath: String) {
        filePkg[filePath] = pkgFqn
        packageByFqn.getOrPut(pkgFqn) {
            val meta = NodeMeta(
                source = "onPackage",
                pkgFqn = pkgFqn
            )
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
                meta = meta
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
    ) = onTypeEx(kind, fqn, pkgFqn, name, filePath, spanLines, supertypesSimple, null, null, null, null)

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
        kdocMeta: Map<String, Any?>?,
    ) {
        val pkgNode = packageByFqn.getOrPut(pkgFqn) {
            val meta = NodeMeta(
                source = "onType:pkgAuto",
                pkgFqn = pkgFqn
            )
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
                meta = meta
            )
        }

        val meta = NodeMeta(
            source = "onType",
            pkgFqn = pkgFqn,
            supertypesSimple = supertypesSimple,
            imports = fileImports[filePath],
            kdoc = kdocMeta?.let {
                KDocMeta(
                    summary = it["summary"] as? String,
                    details = it["details"] as? String,
                    tags = (it["tags"] as? Map<*, *>)?.entries?.associate { e ->
                        e.key.toString() to e.value.toString()
                    }
                )
            }
        )

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
            meta = meta
        )
        typeByFqn[fqn] = typeNode
    }

    // -------------------- FIELD (basic) --------------------

    override fun onField(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
    ) = onFieldEx(ownerFqn, name, filePath, spanLines, null, null, null)

    // -------------------- FIELD (extended) --------------------

    override fun onFieldEx(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        sourceCode: String?,
        docComment: String?,
        kdocMeta: Map<String, Any?>?,
    ) {
        val pkg = filePkg[filePath]
        val parent = typeByFqn[ownerFqn] ?: packageByFqn[pkg.orEmpty()]

        val meta = NodeMeta(
            source = "onField",
            pkgFqn = pkg,
            ownerFqn = ownerFqn,
            kdoc = kdocMeta?.let {
                KDocMeta(
                    summary = it["summary"] as? String,
                    details = it["details"] as? String,
                    tags = (it["tags"] as? Map<*, *>)?.entries?.associate { e ->
                        e.key.toString() to e.value.toString()
                    }
                )
            }
        )

        upsertNode(
            fqn = "$ownerFqn.$name",
            kind = NodeKind.FIELD,
            name = name,
            packageName = pkg,
            parent = parent,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = null, // можно распарсить позднее
            sourceCode = sourceCode,
            docComment = docComment,
            meta = meta
        )
    }

    // -------------------- FUNCTION (basic) --------------------

    override fun onFunction(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        usages: List<RawUsage>,
    ) = onFunctionEx(ownerFqn, name, paramNames, filePath, spanLines, usages, null, null, null, null, null)

    // -------------------- FUNCTION (extended) --------------------

    override fun onFunctionEx(
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
        kdocMeta: Map<String, Any?>?,
    ) {
        val kind =
            when {
                annotations.isNullOrEmpty() -> NodeKind.METHOD
                annotations.any { it.endsWith("Mapping") } -> NodeKind.ENDPOINT
                "Scheduled" in annotations -> NodeKind.JOB
                "KafkaListener" in annotations -> NodeKind.TOPIC
                else -> NodeKind.METHOD
            }

        val pkgFqn = filePkg[filePath]
        val fqn =
            when {
                !ownerFqn.isNullOrBlank() -> "$ownerFqn.$name"
                !pkgFqn.isNullOrBlank() -> "$pkgFqn.$name"
                else -> name
            }

        val sig = signature ?: buildString {
            append(name).append('(').append(paramNames.joinToString(",")).append(')')
        }

        // Фильтрация «шума» в вызовах
        val callsFiltered = usages.filterNot { usage ->
            when (usage) {
                is RawUsage.Dot -> usage.receiver in noise || usage.member in noise
                is RawUsage.Simple -> usage.name in noise
            }
        }

        val parent =
            when {
                ownerFqn != null -> typeByFqn[ownerFqn]
                pkgFqn != null -> packageByFqn[pkgFqn]
                else -> null
            }

        val meta = NodeMeta(
            source = "onFunction",
            pkgFqn = pkgFqn,
            ownerFqn = ownerFqn,
            params = paramNames,
            rawUsages = callsFiltered,
            annotations = annotations?.toList(),
            imports = fileImports[filePath],
            kdoc = kdocMeta?.let {
                KDocMeta(
                    summary = it["summary"] as? String,
                    details = it["details"] as? String,
                    tags = (it["tags"] as? Map<*, *>)?.entries?.associate { e ->
                        e.key.toString() to e.value.toString()
                    }
                )
            }
        )

        val fnNode = upsertNode(
            fqn = fqn,
            kind = kind,
            name = name,
            packageName = pkgFqn,
            parent = parent,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = sig,
            sourceCode = sourceCode,
            docComment = docComment,
            meta = meta
        )
        funcByFqn[fqn] = fnNode
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
                    meta = metaMap
                )
            )
        } else {
            var changed = false

            fun <T> setIfChanged(curr: T, new: T, apply: (T) -> Unit) {
                if (curr != new) {
                    apply(new)
                    changed = true
                }
            }

            setIfChanged(existing.name, name) { existing.name = it }
            setIfChanged(existing.packageName, packageName) { existing.packageName = it }
            setIfChanged(existing.kind, kind) { existing.kind = it }
            setIfChanged(existing.lang, lang) { existing.lang = it }
            setIfChanged(existing.parent?.id, parent?.id) { existing.parent = parent }
            setIfChanged(existing.filePath, filePath) { existing.filePath = it }
            setIfChanged(existing.lineStart, span?.first) { existing.lineStart = it }
            setIfChanged(existing.lineEnd, span?.last) { existing.lineEnd = it }
            setIfChanged(existing.sourceCode, sourceCode) { existing.sourceCode = it }
            setIfChanged(existing.docComment, docComment) { existing.docComment = it }
            setIfChanged(existing.signature, signature) { existing.signature = it }

            // merge meta (старое + новое), потом чистим пустое
            @Suppress("UNCHECKED_CAST")
            val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
            val merged = (currentMeta + metaMap).cleaned()
            setIfChanged(existing.meta, merged) { existing.meta = it }

            if (changed) nodeRepo.save(existing) else existing
        }
    }

    // -------------------- УТИЛИТЫ --------------------

    /** Удаляем пустые поля у NodeMeta уже после convertValue → Map (чтобы JSONB был компактным). */
    private fun Map<String, Any?>.cleaned(): Map<String, Any> =
        entries.asSequence()
            .filter { (_, v) ->
                v != null && when (v) {
                    is Collection<*> -> v.isNotEmpty()
                    is Map<*, *> -> v.isNotEmpty()
                    else -> true
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

    // -------------------- FILE CONTEXT --------------------

    override fun onFileContext(pkgFqn: String, filePath: String, imports: List<String>) {
        filePkg[filePath] = pkgFqn
        fileImports[filePath] = imports
    }
}
