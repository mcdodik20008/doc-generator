package com.bftcom.docgenerator.graph.api.linker.indexing

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node

interface NodeIndex {
    fun findByFqn(fqn: String): Node?
    fun findByKind(kind: NodeKind): Sequence<Node>
    fun findAnnotatedWith(annotation: String): Sequence<Node>
}
