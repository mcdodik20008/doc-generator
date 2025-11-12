package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

@Component
class NodeIndexFactory {
    fun create(all: List<Node>): NodeIndex = SnapshotNodeIndex(all)

    private class SnapshotNodeIndex(val all: List<Node>) : NodeIndex {

        private val byFqn = all.associateBy { it.fqn }
        private val bySimple = all.groupBy { it.name }
        private val packages = all.filter { it.kind == NodeKind.PACKAGE }.associateBy { it.fqn }

        override fun findByFqn(fqn: String): Node? = byFqn[fqn]

        override fun findByKind(kind: NodeKind) = all.asSequence().filter { it.kind == kind }

        override fun findAnnotatedWith(annotation: String) =
            all.asSequence().filter { it.meta["annotations"]?.toString()?.contains(annotation) == true }

        override fun resolveType(simpleOrFqn: String, imports: List<String>, pkg: String): Node? {
            byFqn[simpleOrFqn]?.let { return it }
            val simple = simpleOrFqn.substringAfterLast('.').removeSuffix("?").substringBefore('<')
            imports.firstOrNull { it.endsWith(".$simple") }?.let { byFqn[it] }?.let { return it }
            byFqn["$pkg.$simple"]?.let { return it }
            return bySimple[simple]?.firstOrNull()
        }
    }
}
