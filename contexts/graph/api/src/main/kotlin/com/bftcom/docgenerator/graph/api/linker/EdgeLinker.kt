package com.bftcom.docgenerator.graph.api.linker

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex

/**
 * Стратегия линковки рёбер графа.
 * Каждый линкер отвечает за создание рёбер определённого типа.
 */
interface EdgeLinker {
    /**
     * Создаёт рёбра для указанного узла.
     * @param node Узел для линковки
     * @param meta Метаданные узла
     * @param index Индекс узлов для поиска связанных узлов
     * @return Список троек (источник, назначение, тип ребра)
     */
    fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>>
}

