package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для связей наследования и реализации (INHERITS, IMPLEMENTS).
 * Применяется только к узлам типов (CLASS, INTERFACE, etc.).
 */
@Component
class InheritanceEdgeLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()
        val candidates = (meta.supertypesResolved ?: emptyList()) + (meta.supertypesSimple ?: emptyList())

        for (raw in candidates) {
            val target = index.resolveType(raw, imports, pkg) ?: continue
            when (target.kind) {
                NodeKind.INTERFACE -> {
                    res += Triple(node, target, EdgeKind.IMPLEMENTS)
                    res += Triple(node, target, EdgeKind.DEPENDS_ON)
                }
                else -> {
                    res += Triple(node, target, EdgeKind.INHERITS)
                    res += Triple(node, target, EdgeKind.DEPENDS_ON)
                }
            }
        }
        return res
    }
}

