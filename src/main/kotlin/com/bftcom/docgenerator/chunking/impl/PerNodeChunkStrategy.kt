package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.model.ChunkPlan
import com.bftcom.docgenerator.chunking.ChunkStrategy
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.stereotype.Component

@Component("per-node")
class PerNodeChunkStrategy : ChunkStrategy {
    override fun buildChunks(node: Node, edges: List<Edge>): List<ChunkPlan> {
        val plans = mutableListOf<ChunkPlan>()

        node.sourceCode?.let { code ->
            val relations: List<Map<String, Any>> = edges.filter { it.kind == EdgeKind.CALLS }.map { e ->
                mapOf("kind" to "CALLS", "dst_node_id" to e.dst.id, "confidence" to 0.7) as Map<String, Any>
            }

            val sectionPath = buildList {
                node.packageName?.split('.')?.let { addAll(it) }
                node.name?.let { add(it) }
            }

            plans += ChunkPlan(
                source = "code",
                kind = "snippet",
                content = code,
                langDetected = node.lang.name,
                spanLines = rangeClosed(node.lineStart, node.lineEnd),
                title = node.fqn,
                sectionPath = sectionPath,
                relations = relations,
                usedObjects = emptyList(),
                pipeline = mapOf("strategy" to "per-node", "from" to "node.sourceCode"),
                node = node
            )
        }

        // пример doc-чанка из сигнатуры/коммента
        if (!node.signature.isNullOrBlank() || !node.docComment.isNullOrBlank()) {
            val md = buildString {
                node.signature?.let { appendLine("**Signature:** `$it`") }
                node.docComment?.let { appendLine(); appendLine(it.trim()) }
            }.ifBlank { null }

            if (md != null) {
                plans += ChunkPlan(
                    source = "doc",
                    kind = "explanation",
                    content = md,
                    langDetected = "ru",
                    spanLines = rangeClosed(node.lineStart, node.lineEnd),
                    title = node.fqn,
                    sectionPath = listOfNotNull(node.packageName, node.name).flatMap { it.split('.') },
                    relations = emptyList(),
                    usedObjects = emptyList(),
                    pipeline = mapOf("strategy" to "per-node", "from" to "signature+docComment"),
                    node = node
                )
            }
        }

        return plans
    }

    private fun rangeClosed(s: Int?, e: Int?): String? =
        if (s == null || e == null) null else "[${minOf(s,e)},${maxOf(s,e)}]"
}
