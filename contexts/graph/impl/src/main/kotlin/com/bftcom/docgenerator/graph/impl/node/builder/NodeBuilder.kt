package com.bftcom.docgenerator.graph.impl.node.builder

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Строитель нод - отвечает за создание и обновление Node сущностей.
 */
class NodeBuilder(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
) {
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
            updateExistingNode(existing, name, packageName, kind, lang, parent, filePath, lineStart, lineEnd, sourceCode, docComment, signature, metaMap)
        }
    }

    private fun updateExistingNode(
        existing: Node,
        name: String?,
        packageName: String?,
        kind: NodeKind,
        lang: Lang,
        parent: Node?,
        filePath: String?,
        lineStart: Int?,
        lineEnd: Int?,
        sourceCode: String?,
        docComment: String?,
        signature: String?,
        metaMap: Map<String, Any>,
    ): Node {
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

        return if (changed) {
            nodeRepo.save(existing)
        } else {
            existing
        }
    }

    private fun countLinesNormalized(src: String): Int {
        if (src.isEmpty()) return 0
        var c = 1
        for (ch in src.replace("\r\n", "\n")) if (ch == '\n') c++
        return c
    }

    private fun toMetaMap(meta: NodeMeta): Map<String, Any> = 
        objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>
}

