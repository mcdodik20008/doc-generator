package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для связей вызовов методов (CALLS).
 */
@Component
class CallEdgeLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val usages = meta.rawUsages ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
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
                    if (u.checkIsCall()) {
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
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
                    recvType?.let { r ->
                        index.findByFqn("${r.fqn}.${u.member}")?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                        }
                    }
                }
            }
        }
        return res
    }
}

