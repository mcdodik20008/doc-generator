package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для структурных связей CONTAINS.
 * Создаёт связи между пакетами и типами, а также между типами и их членами.
 * Работает со списком всех узлов, так как структурные связи требуют полного обзора.
 */
@Component
class StructuralEdgeLinker {
    /**
     * Создаёт структурные связи CONTAINS для всех узлов.
     * @param all Список всех узлов
     * @param index Индекс узлов для поиска
     * @param metaOf Функция для получения метаданных узла
     * @return Список троек (источник, назначение, тип ребра)
     */
    fun linkContains(
        all: List<Node>,
        index: NodeIndex,
        metaOf: (Node) -> NodeMeta,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()

        // Связи между пакетами и типами
        all
            .filter { it.kind in TYPE_KINDS }
            .forEach { type ->
                val pkg = index.findByFqn(type.packageName ?: return@forEach) ?: return@forEach
                res += Triple(pkg, type, EdgeKind.CONTAINS)
            }

        // Связи между типами и их членами
        all
            .filter { it.kind in MEMBER_KINDS }
            .forEach { member ->
                val ownerFqn = metaOf(member).ownerFqn ?: return@forEach
                val owner = index.findByFqn(ownerFqn) ?: return@forEach
                res += Triple(owner, member, EdgeKind.CONTAINS)
            }

        return res
    }

    companion object {
        private val TYPE_KINDS = setOf(
            NodeKind.INTERFACE,
            NodeKind.SERVICE,
            NodeKind.RECORD,
            NodeKind.MAPPER,
            NodeKind.ENDPOINT,
            NodeKind.CLASS,
            NodeKind.ENUM,
            NodeKind.CONFIG,
        )

        private val MEMBER_KINDS = setOf(
            NodeKind.METHOD,
            NodeKind.FIELD,
            NodeKind.ENDPOINT,
            NodeKind.JOB,
            NodeKind.TOPIC,
        )
    }
}

