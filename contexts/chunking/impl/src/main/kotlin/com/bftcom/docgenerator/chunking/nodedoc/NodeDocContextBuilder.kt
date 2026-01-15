package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeDocRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NodeDocContextBuilder(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val nodeDocRepo: NodeDocRepository,
    @param:Value("\${docgen.nodedoc.method.max-code-chars:8000}")
    private val methodMaxCodeChars: Int,
    @param:Value("\${docgen.nodedoc.deps.topk:20}")
    private val depsTopK: Int,
    @param:Value("\${docgen.nodedoc.children.topk:40}")
    private val childrenTopK: Int,
) {
    data class BuildResult(
        val context: String,
        val depsMissing: Boolean,
        val depsForDigest: List<String>,
        val included: List<Map<String, Any>>,
    )

    fun build(node: Node, locale: String): BuildResult =
        when (node.kind) {
            NodeKind.METHOD -> buildMethod(node, locale)
            NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.RECORD -> buildType(node, locale)
            NodeKind.PACKAGE -> buildContainer(node, locale, containerKind = "PACKAGE")
            NodeKind.MODULE -> buildContainer(node, locale, containerKind = "MODULE")
            NodeKind.REPO -> buildContainer(node, locale, containerKind = "REPO")
            else -> buildLeaf(node, locale)
        }

    private fun buildMethod(node: Node, locale: String): BuildResult {
        val nodeId = node.id ?: error("node.id is null")
        val edges = edgeRepo.findAllBySrcId(nodeId)
        val prio =
            listOf(
                EdgeKind.CALLS_CODE,
                EdgeKind.THROWS,
                EdgeKind.READS,
                EdgeKind.WRITES,
                EdgeKind.QUERIES,
                EdgeKind.CALLS_HTTP,
                EdgeKind.CALLS_GRPC,
                EdgeKind.PRODUCES,
                EdgeKind.CONSUMES,
                EdgeKind.CONFIGURES,
                EdgeKind.CIRCUIT_BREAKER_TO,
                EdgeKind.RETRIES_TO,
                EdgeKind.TIMEOUTS_TO,
                EdgeKind.DEPENDS_ON,
            )
        val prioIndex = prio.withIndex().associate { it.value to it.index }

        val picked =
            edges
                .sortedWith(compareBy({ prioIndex[it.kind] ?: 999 }, { it.dst.id ?: Long.MAX_VALUE }))
                .take(depsTopK)

        val dstIds = picked.mapNotNull { it.dst.id }.toSet()
        val dstNodes = nodeRepo.findAllByIdIn(dstIds).associateBy { it.id!! }

        val included = mutableListOf<Map<String, Any>>()
        val depsMissing = mutableListOf<Long>()
        val depsForDigest = mutableListOf<String>()

        val depsBlock =
            buildString {
                appendLine("## Dependency digests (topK=$depsTopK)")
                if (picked.isEmpty()) {
                    appendLine("- none")
                } else {
                    for (e in picked) {
                        val dstId = e.dst.id ?: continue
                        val dst = dstNodes[dstId]
                        val digest = nodeDocRepo.findDigest(dstId, locale)
                        val dstFqn = dst?.fqn ?: "node#$dstId"
                        depsForDigest += dstFqn
                        included +=
                            mapOf(
                                "node_id" to dstId,
                                "kind" to (dst?.kind?.name ?: "unknown"),
                                "edge_kind" to e.kind.name,
                                "level" to "dep",
                            )
                        if (digest.isNullOrBlank()) {
                            depsMissing += dstId
                            appendLine("- edge=${e.kind.name} dst=$dstFqn digest=missing")
                        } else {
                            appendLine("- edge=${e.kind.name} dst=$dstFqn")
                            appendLine(digest.prependIndent("  "))
                        }
                    }
                }
            }

        val code = (node.sourceCode ?: "").trim().take(methodMaxCodeChars)
        val kdoc = (node.docComment ?: "").trim()
        val sig = node.signature?.trim().orEmpty()

        val ctx =
            buildString {
                appendLine("## Node")
                appendLine("kind=${node.kind.name}")
                appendLine("fqn=${node.fqn}")
                if (sig.isNotBlank()) appendLine("signature=$sig")
                appendLine()
                if (kdoc.isNotBlank()) {
                    appendLine("## KDoc")
                    appendLine(kdoc.take(4000))
                    appendLine()
                }
                appendLine("## Code (body only, truncated=${code.length < (node.sourceCode ?: "").length})")
                appendLine("```")
                appendLine(code.ifBlank { "// no source_code" })
                appendLine("```")
                appendLine()
                append(depsBlock)
            }

        included += mapOf("node_id" to nodeId, "kind" to node.kind.name, "level" to "self")
        return BuildResult(
            context = ctx,
            depsMissing = depsMissing.isNotEmpty(),
            depsForDigest = depsForDigest.distinct().take(50),
            included = included,
        )
    }

    private fun buildType(node: Node, locale: String): BuildResult {
        val nodeId = node.id ?: error("node.id is null")
        val children =
            nodeRepo
                .findAllByParentId(nodeId)
                .filter { it.kind == NodeKind.METHOD || it.kind == NodeKind.FIELD }
                .take(childrenTopK)

        val included = mutableListOf<Map<String, Any>>()
        val depsMissing = mutableListOf<Long>()
        val depsForDigest = mutableListOf<String>()

        val childDigests =
            buildString {
                appendLine("## Children digests (topK=$childrenTopK)")
                if (children.isEmpty()) {
                    appendLine("- none")
                } else {
                    for (ch in children) {
                        val chId = ch.id ?: continue
                        val digest = nodeDocRepo.findDigest(chId, locale)
                        depsForDigest += ch.fqn
                        included += mapOf("node_id" to chId, "kind" to ch.kind.name, "level" to "child")
                        if (digest.isNullOrBlank()) {
                            depsMissing += chId
                            appendLine("- child=${ch.fqn} digest=missing")
                        } else {
                            appendLine("- child=${ch.fqn}")
                            appendLine(digest.prependIndent("  "))
                        }
                    }
                }
            }

        val kdoc = (node.docComment ?: "").trim()
        val ctx =
            buildString {
                appendLine("## Node")
                appendLine("kind=${node.kind.name}")
                appendLine("fqn=${node.fqn}")
                appendLine()
                if (kdoc.isNotBlank()) {
                    appendLine("## KDoc")
                    appendLine(kdoc.take(4000))
                    appendLine()
                }
                append(childDigests)
            }

        included += mapOf("node_id" to nodeId, "kind" to node.kind.name, "level" to "self")
        return BuildResult(
            context = ctx,
            depsMissing = depsMissing.isNotEmpty(),
            depsForDigest = depsForDigest.distinct().take(50),
            included = included,
        )
    }

    private fun buildContainer(node: Node, locale: String, containerKind: String): BuildResult {
        val nodeId = node.id ?: error("node.id is null")
        val children = nodeRepo.findAllByParentId(nodeId).take(childrenTopK)

        val included = mutableListOf<Map<String, Any>>()
        val missing = mutableListOf<Long>()
        val depsForDigest = mutableListOf<String>()

        val digests =
            buildString {
                appendLine("## Lower-level digests (topK=$childrenTopK)")
                if (children.isEmpty()) {
                    appendLine("- none")
                } else {
                    for (ch in children) {
                        val chId = ch.id ?: continue
                        val digest = nodeDocRepo.findDigest(chId, locale)
                        depsForDigest += ch.fqn
                        included += mapOf("node_id" to chId, "kind" to ch.kind.name, "level" to "child")
                        if (digest.isNullOrBlank()) {
                            missing += chId
                            appendLine("- child=${ch.fqn} digest=missing")
                        } else {
                            appendLine("- child=${ch.fqn}")
                            appendLine(digest.prependIndent("  "))
                        }
                    }
                }
            }

        val ctx =
            buildString {
                appendLine("## Node")
                appendLine("kind=$containerKind")
                appendLine("fqn=${node.fqn}")
                appendLine()
                append(digests)
            }

        included += mapOf("node_id" to nodeId, "kind" to node.kind.name, "level" to "self")
        return BuildResult(ctx, missing.isNotEmpty(), depsForDigest.distinct().take(50), included)
    }

    private fun buildLeaf(node: Node, locale: String): BuildResult {
        val nodeId = node.id ?: error("node.id is null")
        val kdoc = (node.docComment ?: "").trim()
        val code = (node.sourceCode ?: "").trim().take(methodMaxCodeChars)
        val ctx =
            buildString {
                appendLine("## Node")
                appendLine("kind=${node.kind.name}")
                appendLine("fqn=${node.fqn}")
                appendLine()
                if (kdoc.isNotBlank()) {
                    appendLine("## Doc comment")
                    appendLine(kdoc.take(4000))
                    appendLine()
                }
                if (code.isNotBlank()) {
                    appendLine("## Source")
                    appendLine("```")
                    appendLine(code)
                    appendLine("```")
                }
            }
        return BuildResult(
            context = ctx,
            depsMissing = false,
            depsForDigest = emptyList(),
            included = listOf(mapOf("node_id" to nodeId, "kind" to node.kind.name, "level" to "self")),
        )
    }
}

