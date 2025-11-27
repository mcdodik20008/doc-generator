package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для связей аннотаций (ANNOTATED_WITH).
 */
@Component
class AnnotationEdgeLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val annotations = meta.annotations ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        for (a in annotations) {
            val t = index.resolveType(a, imports, pkg) ?: continue
            res += Triple(node, t, EdgeKind.ANNOTATED_WITH)
            res += Triple(node, t, EdgeKind.DEPENDS_ON)
        }
        return res
    }
}

