package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Линкер для связей вызовов методов (CALLS).
 */
@Component
class CallEdgeLinker : EdgeLinker {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        return try {
            doLink(node, meta, index)
        } catch (e: Exception) {
            log.error("Error linking CALLS edges for node fqn={}: {}", node.fqn, e.message, e)
            emptyList()
        }
    }

    private fun doLink(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val usages = meta.rawUsages ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
        if (owner == null && meta.ownerFqn != null) {
            log.trace("Owner not found for fqn={}, ownerFqn={}", node.fqn, meta.ownerFqn)
        }
        val pkg = node.packageName.orEmpty()

        usages.forEach { u ->
            when (u) {
                is RawUsage.Simple -> {
                    if (owner != null) {
                        index.findByFqn("${owner.fqn}.${u.name}")?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                            return@forEach
                        }
                    }
                    // NOTE: checkIsCall() uses heuristics; overloaded methods are matched by name only
                    if (u.checkIsCall()) {
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                        }
                    }
                }
                is RawUsage.Dot -> {
                    // NOTE: isUpperCase() heuristic doesn't cover all Kotlin conventions (companion objects, extensions)
                    val recvType =
                        if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                            index.resolveType(u.receiver, imports, pkg)
                        } else {
                            owner
                        }
                    if (recvType == null) {
                        log.trace("Could not resolve receiver type for dot usage: receiver={}, member={}, node={}", u.receiver, u.member, node.fqn)
                    }
                    recvType?.let { r ->
                        val target = index.findByFqn("${r.fqn}.${u.member}")
                        if (target != null) {
                            res += Triple(node, target, EdgeKind.CALLS)
                        } else {
                            log.trace("Member not found: {}.{} (from node {})", r.fqn, u.member, node.fqn)
                        }
                    }
                }
            }
        }
        return res
    }
}
