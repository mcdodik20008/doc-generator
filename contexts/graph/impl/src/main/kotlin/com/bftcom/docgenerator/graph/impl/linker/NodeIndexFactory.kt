package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

@Component
class NodeIndexFactory {
    companion object {
        private val CALLABLE_KINDS = setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB)
    }
    fun create(all: List<Node>): NodeIndex = SnapshotNodeIndex(all)

    fun createMutable(initial: List<Node>): MutableNodeIndex = MutableNodeIndex(initial)

    private class SnapshotNodeIndex(
        val all: List<Node>,
    ) : NodeIndex {
        private val byFqn = all.associateBy { it.fqn }
        private val bySimple = all.groupBy { it.name }
        private val byBaseFqn: Map<String, List<Node>> = all
            .filter { it.kind in CALLABLE_KINDS }
            .groupBy { it.fqn.substringBefore('(') }

        override fun findByFqn(fqn: String): Node? = byFqn[fqn]

        override fun findByKind(kind: NodeKind) = all.asSequence().filter { it.kind == kind }

        override fun findAnnotatedWith(annotation: String) =
            all.asSequence().filter { it.meta["annotations"]?.toString()?.contains(annotation) == true }

        override fun resolveType(
            simpleOrFqn: String,
            imports: List<String>,
            pkg: String,
        ): Node? {
            byFqn[simpleOrFqn]?.let { return it }
            val simple = simpleOrFqn.substringAfterLast('.').removeSuffix("?").substringBefore('<')
            imports.firstOrNull { it.endsWith(".$simple") }?.let { byFqn[it] }?.let { return it }
            byFqn["$pkg.$simple"]?.let { return it }
            return bySimple[simple]?.firstOrNull()
        }

        override fun findMethodsByName(baseFqn: String): List<Node> = byBaseFqn[baseFqn].orEmpty()
    }

    /**
     * Мутабельный индекс, который можно обновлять новыми узлами.
     */
    class MutableNodeIndex(
        initial: List<Node>,
    ) : NodeIndex {
        private val all = initial.toMutableList()
        private val byFqn = initial.associateBy { it.fqn }.toMutableMap()
        private val bySimple = initial.groupBy { it.name }.toMutableMap()
        private val packages = initial.filter { it.kind == NodeKind.PACKAGE }.associateBy { it.fqn }.toMutableMap()
        private val byBaseFqn: MutableMap<String, MutableList<Node>> = initial
            .filter { it.kind in CALLABLE_KINDS }
            .groupByTo(mutableMapOf()) { it.fqn.substringBefore('(') }

        fun addNode(node: Node) {
            if (byFqn.containsKey(node.fqn)) {
                // Узел уже есть, обновляем
                val oldIndex = all.indexOfFirst { it.fqn == node.fqn }
                if (oldIndex >= 0) {
                    all[oldIndex] = node
                }
            } else {
                // Новый узел
                all.add(node)
            }
            byFqn[node.fqn] = node
            val updated = (bySimple[node.name] ?: emptyList()) + node
            bySimple[node.name] = updated
            if (node.kind == NodeKind.PACKAGE) {
                packages[node.fqn] = node
            }
            if (node.kind in CALLABLE_KINDS) {
                val base = node.fqn.substringBefore('(')
                byBaseFqn.getOrPut(base) { mutableListOf() }.add(node)
            }
        }

        fun addNodes(nodes: List<Node>) {
            nodes.forEach { addNode(it) }
        }

        override fun findByFqn(fqn: String): Node? = byFqn[fqn]

        override fun findByKind(kind: NodeKind) = all.asSequence().filter { it.kind == kind }

        override fun findAnnotatedWith(annotation: String) =
            all.asSequence().filter { it.meta["annotations"]?.toString()?.contains(annotation) == true }

        override fun resolveType(
            simpleOrFqn: String,
            imports: List<String>,
            pkg: String,
        ): Node? {
            byFqn[simpleOrFqn]?.let { return it }
            val simple = simpleOrFqn.substringAfterLast('.').removeSuffix("?").substringBefore('<')
            imports.firstOrNull { it.endsWith(".$simple") }?.let { byFqn[it] }?.let { return it }
            byFqn["$pkg.$simple"]?.let { return it }
            return bySimple[simple]?.firstOrNull()
        }

        override fun findMethodsByName(baseFqn: String): List<Node> = byBaseFqn[baseFqn].orEmpty()
    }
}
