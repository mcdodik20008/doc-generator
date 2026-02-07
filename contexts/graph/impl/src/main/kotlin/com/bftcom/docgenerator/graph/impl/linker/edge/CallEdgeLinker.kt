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
 * Линкер для связей вызовов методов (CALLS_CODE).
 */
@Component
class CallEdgeLinker : EdgeLinker {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        return try {
            doLink(node, meta, index)
        } catch (e: Exception) {
            log.error("Error linking CALLS_CODE edges for node fqn={}: {}", node.fqn, e.message, e)
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
                        val baseFqn = "${owner.fqn}.${u.name}"
                        val exact = index.findByFqn(baseFqn)
                        if (exact != null) {
                            res += Triple(node, exact, EdgeKind.CALLS_CODE)
                            return@forEach
                        }
                        val overloads = index.findMethodsByName(baseFqn)
                        if (overloads.isNotEmpty()) {
                            overloads.forEach { target ->
                                res += Triple(node, target, EdgeKind.CALLS_CODE)
                            }
                            return@forEach
                        }
                    }
                    if (u.checkIsCall()) {
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(node, it, EdgeKind.CALLS_CODE)
                        }
                    }
                }
                is RawUsage.Dot -> {
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
                        val baseFqn = "${r.fqn}.${u.member}"
                        val target = index.findByFqn(baseFqn)
                        if (target != null) {
                            res += Triple(node, target, EdgeKind.CALLS_CODE)
                        } else {
                            val overloads = index.findMethodsByName(baseFqn)
                            if (overloads.isNotEmpty()) {
                                overloads.forEach { t ->
                                    res += Triple(node, t, EdgeKind.CALLS_CODE)
                                }
                            } else {
                                log.trace("Member not found: {}.{} (from node {})", r.fqn, u.member, node.fqn)
                            }
                        }
                    }
                }
            }
        }
        return res
    }
}
