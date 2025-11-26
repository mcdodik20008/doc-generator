package com.bftcom.docgenerator.graph.api.linker.sink

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.domain.node.Node

/**
 * Интерфейс для сохранения связей между узлами приложения и узлами библиотек.
 */
interface LibraryNodeGraphSink {
    /**
     * Сохраняет связи между Node и LibraryNode.
     */
    fun upsertLibraryNodeEdges(edges: Sequence<LibraryNodeEdgeProposal>)
}

/**
 * Предложение связи между Node и LibraryNode.
 */
data class LibraryNodeEdgeProposal(
    val kind: EdgeKind,
    val node: Node,
    val libraryNode: LibraryNode,
)

