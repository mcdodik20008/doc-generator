package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkStrategy
import com.bftcom.docgenerator.chunking.model.*
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.stereotype.Component

@Component("per-node")
class PerNodeChunkStrategy : ChunkStrategy {
    override fun buildChunks(
        node: Node,
        edges: List<Edge>,
    ): List<ChunkPlan> {
        val hasDoc = !node.signature.isNullOrBlank() || !node.docComment.isNullOrBlank()
        val sectionPath =
            buildList {
                node.packageName?.split('.')?.let { addAll(it) }
                node.name?.let { add(it) }
            }

        val relations =
            edges
                .asSequence()
                .filter { it.kind == EdgeKind.CALLS }
                .map { e -> RelationHint(kind = "CALLS", dstNodeId = e.dst.id ?: -1, confidence = 0.7) }
                .toList()

        return listOf(
            if (hasDoc) {
                // 1) DOC-чанк (эксплейнер из сигнатуры/док-коммента)
                ChunkPlan(
                    id = chunkId(node, source = "doc", kind = "explanation"),
                    nodeId = node.id!!,
                    source = "doc",
                    kind = "explanation",
                    lang = "ru",
                    spanLines = toRange(node.lineStart, node.lineEnd),
                    title = node.fqn,
                    sectionPath = sectionPath,
                    relations = relations, // можно и пусто — по вкусу
                    pipeline =
                        PipelinePlan(
                            stages = listOf("render-doc", "embed", "link-edges"),
                            params =
                                mapOf(
                                    // только лёгкие данные; тяжёлый контент НЕ кладём
                                    "signature" to (node.signature ?: ""),
                                    "hasDocComment" to (!node.docComment.isNullOrBlank()),
                                ),
                            service = ServiceMeta(strategy = "per-node", priority = priorityFor(node)),
                        ),
                    node = node,
                )
            } else {
                // 2) CODE-чанк (когда нет документации)
                ChunkPlan(
                    id = chunkId(node, source = "code", kind = "snippet"),
                    nodeId = node.id!!,
                    source = "code",
                    kind = "snippet",
                    lang = node.lang.name,
                    spanLines = toRange(node.lineStart, node.lineEnd),
                    title = node.fqn,
                    sectionPath = sectionPath,
                    relations = relations,
                    pipeline =
                        PipelinePlan(
                            stages = listOf("extract-snippet", "summarize", "embed", "link-edges"),
                            params =
                                mapOf(
                                    "filePath" to (node.filePath ?: ""),
                                    "hasSourceInNode" to (node.sourceCode != null),
                                ),
                            service = ServiceMeta(strategy = "per-node", priority = priorityFor(node)),
                        ),
                    node = node,
                )
            },
        )
    }

    private fun toRange(
        s: Int?,
        e: Int?,
    ): IntRange? = if (s == null || e == null) null else minOf(s, e)..maxOf(s, e)

    private fun chunkId(
        node: Node,
        source: String,
        kind: String,
    ): String = "${node.id}:$source:$kind" // детерминированный ID для 1:1 соответствия

    private fun priorityFor(node: Node): Int =
        when (node.kind.name) {
            "ENDPOINT", "METHOD" -> 10
            "CLASS" -> 5
            else -> 0
        }
}
