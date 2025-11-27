package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для связей исключений (THROWS).
 */
@Component
class ThrowEdgeLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val throwsTypes = meta.throwsTypes ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        throwsTypes.forEach { throwType ->
            index.resolveType(throwType, imports, pkg)?.let {
                res += Triple(node, it, EdgeKind.THROWS)
            }
        }
        return res
    }
}

