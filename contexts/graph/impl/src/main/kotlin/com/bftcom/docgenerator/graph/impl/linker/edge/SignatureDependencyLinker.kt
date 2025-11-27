package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для зависимостей из сигнатуры метода (DEPENDS_ON).
 * Извлекает типы из параметров и возвращаемого типа.
 */
@Component
class SignatureDependencyLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        val tokens: Set<String> =
            when {
                !meta.paramTypes.isNullOrEmpty() || !meta.returnType.isNullOrBlank() ->
                    (meta.paramTypes.orEmpty() + listOfNotNull(meta.returnType)).toSet()
                !node.signature.isNullOrBlank() ->
                    TYPE_TOKEN
                        .findAll(node.signature!!)
                        .map { it.groupValues[1].substringBefore('<').substringBefore('?') }
                        .toSet()
                else -> emptySet()
            }

        val ownerFqn = meta.ownerFqn
        val src = ownerFqn?.let { index.findByFqn(it) } ?: node

        for (t in tokens) {
            val typeNode = index.resolveType(t, imports, pkg) ?: continue
            if (typeNode.id != src.id) res += Triple(src, typeNode, EdgeKind.DEPENDS_ON)
        }
        return res
    }

    companion object {
        private val TYPE_TOKEN = Regex("""\:\s*([A-Za-z_][A-Za-z0-9_\.]*)""")
    }
}

