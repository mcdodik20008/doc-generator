package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.CommandExecutor
import com.bftcom.docgenerator.graph.api.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.fasterxml.jackson.databind.ObjectMapper

class CommandExecutorImpl(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
    private val nodeKindRefiner: NodeKindRefiner,
) : CommandExecutor {
    // Локальное состояние сборки графа
    private val packageByFqn = mutableMapOf<String, Node>()
    private val typeByFqn = mutableMapOf<String, Node>()
    private val funcByFqn = mutableMapOf<String, Node>()
    private val filePkg = mutableMapOf<String, String>()
    private val fileImports = mutableMapOf<String, List<String>>()
    private val fileUnitByPath = mutableMapOf<String, RawFileUnit>()

    override fun execute(cmd: DeclCmd) {
        when (cmd) {
            is RememberFileUnitCmd -> {
                val u = cmd.unit
                fileUnitByPath[u.filePath] = u
                if (u.pkgFqn != null) {
                    filePkg[u.filePath] = u.pkgFqn!!
                    fileImports[u.filePath] = u.imports
                } else {
                    filePkg[u.filePath] = ""
                    fileImports[u.filePath] = u.imports
                }
            }

            is EnsurePackageCmd -> {
                val pkg = cmd.pkgFqn
                packageByFqn.getOrPut(pkg) {
                    upsertNode(
                        fqn = pkg,
                        kind = NodeKind.PACKAGE,
                        name = pkg.substringAfterLast('.'),
                        packageName = pkg,
                        parent = null,
                        lang = Lang.kotlin,
                        filePath = cmd.filePath,
                        span = cmd.spanStart..cmd.spanEnd,
                        signature = null,
                        sourceCode = cmd.sourceText,
                        docComment = null,
                        meta = NodeMeta(source = "package/fileUnit", pkgFqn = pkg),
                    )
                }
            }

            is UpsertTypeCmd -> {
                val r = cmd.raw
                val pkgFqn = r.pkgFqn ?: filePkg[r.filePath].orEmpty()
                val fqn = listOfNotNull(pkgFqn.takeIf { it.isNotBlank() }, r.simpleName).joinToString(".")

                val pkgNode =
                    if (pkgFqn.isNotBlank()) {
                        packageByFqn.getOrPut(pkgFqn) {
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
                                meta = NodeMeta(source = "type:pkgAuto", pkgFqn = pkgFqn),
                            )
                        }
                    } else {
                        null
                    }

                val kind = nodeKindRefiner.forType(cmd.baseKind, r, fileUnitByPath[r.filePath])

                val node =
                    upsertNode(
                        fqn = fqn,
                        kind = kind,
                        name = r.simpleName,
                        packageName = pkgFqn.ifBlank { null },
                        parent = pkgNode,
                        lang = Lang.kotlin,
                        filePath = r.filePath,
                        span = r.span?.let { it.start..it.end },
                        signature = r.attributes["signature"] as? String,
                        sourceCode = r.text,
                        docComment = null,
                        meta =
                            NodeMeta(
                                source = "type",
                                pkgFqn = pkgFqn.ifBlank { null },
                                supertypesSimple = r.supertypesRepr,
                                imports = fileImports[r.filePath],
                                kdoc = null,
                                annotations = r.annotationsRepr,
                            ),
                    )
                typeByFqn[fqn] = node
            }

            is UpsertFieldCmd -> {
                val r = cmd.raw
                val pkg = r.pkgFqn ?: filePkg[r.filePath]
                val parent = r.ownerFqn?.let { typeByFqn[it] } ?: pkg?.let { packageByFqn[it] }
                val fieldKind = nodeKindRefiner.forField(NodeKind.FIELD, r, fileUnitByPath[r.filePath])
                upsertNode(
                    fqn = listOfNotNull(r.ownerFqn, r.name).joinToString("."),
                    kind = fieldKind,
                    name = r.name,
                    packageName = pkg,
                    parent = parent,
                    lang = Lang.kotlin,
                    filePath = r.filePath,
                    span = r.span?.let { it.start..it.end },
                    signature = null,
                    sourceCode = r.text,
                    docComment = r.kdoc,
                    meta =
                        NodeMeta(
                            source = "field",
                            pkgFqn = pkg,
                            ownerFqn = r.ownerFqn,
                            kdoc = r.kdoc?.let { KDocMeta(summary = it) },
                            annotations = r.annotationsRepr,
                        ),
                )
            }

            is UpsertFunctionCmd -> {
                val r = cmd.raw
                val pkgFqn = r.pkgFqn ?: filePkg[r.filePath]
                val fqn =
                    when {
                        !r.ownerFqn.isNullOrBlank() -> "${r.ownerFqn}.${r.name}"
                        !pkgFqn.isNullOrBlank() -> "$pkgFqn.${r.name}"
                        else -> r.name
                    }
                val parent =
                    when {
                        !r.ownerFqn.isNullOrBlank() -> typeByFqn[r.ownerFqn!!]
                        !pkgFqn.isNullOrBlank() -> packageByFqn[pkgFqn]
                        else -> null
                    }
                val sig =
                    r.signatureRepr ?: buildString {
                        append(r.name).append('(').append(r.paramNames.joinToString(",")).append(')')
                    }
                val fnKind = nodeKindRefiner.forFunction(NodeKind.METHOD, r, fileUnitByPath[r.filePath])
                val node =
                    upsertNode(
                        fqn = fqn,
                        kind = fnKind,
                        name = r.name,
                        packageName = pkgFqn,
                        parent = parent,
                        lang = Lang.kotlin,
                        filePath = r.filePath,
                        span = r.span?.let { it.start..it.end },
                        signature = sig,
                        sourceCode = r.text,
                        docComment = r.kdoc,
                        meta =
                            NodeMeta(
                                source = "function",
                                pkgFqn = pkgFqn,
                                ownerFqn = r.ownerFqn,
                                params = r.paramNames,
                                rawUsages = r.rawUsages,
                                annotations = r.annotationsRepr.toList(),
                                imports = fileImports[r.filePath],
                                throwsTypes = r.throwsRepr,
                                kdoc = r.kdoc?.let { KDocMeta(summary = it) },
                            ),
                    )
                funcByFqn[fqn] = node
            }
        }
    }

    // --- локальные утилиты и уточнение kind ---
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

        val lineStart: Int? = span?.first
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

            fun <T> setIfChanged(
                curr: T,
                new: T,
                apply: (T) -> Unit,
            ) {
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
            setIfChanged(existing.lineStart, lineStart) { existing.lineStart = it }
            setIfChanged(existing.lineEnd, lineEnd) { existing.lineEnd = it }
            setIfChanged(existing.sourceCode, sourceCode) { existing.sourceCode = it }
            setIfChanged(existing.docComment, docComment) { existing.docComment = it }
            setIfChanged(existing.signature, signature) { existing.signature = it }

            @Suppress("UNCHECKED_CAST")
            val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
            val merged =
                (currentMeta + metaMap).filterValues {
                    it != null &&
                        when (it) {
                            is Collection<*> -> it.isNotEmpty()
                            is Map<*, *> -> it.isNotEmpty()
                            else -> true
                        }
                }
            setIfChanged(existing.meta, merged) { existing.meta = it as Map<String, Any> }

            if (changed) {
                nodeRepo.save(existing)
            } else {
                existing
            }
        }
    }

    private fun countLinesNormalized(src: String): Int {
        if (src.isEmpty()) return 0
        var c = 1
        for (ch in src.replace("\r\n", "\n")) if (ch == '\n') c++
        return c
    }

    private fun toMetaMap(meta: NodeMeta): Map<String, Any> = objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>
}
