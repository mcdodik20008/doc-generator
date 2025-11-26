package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Advisor для расширения окрестности найденных узлов.
 * Находит соседние узлы через рёбра графа и добавляет их в контекст для более полного RAG.
 */
@Component
class NeighborhoodExpansionAdvisor(
    private val edgeRepository: EdgeRepository,
    private val nodeRepository: NodeRepository,
) : QueryProcessingAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = "NeighborhoodExpansion"

    override fun getOrder(): Int = 15 // Выполняется после ExactNodeSearchAdvisor (order=5)

    /**
     * Важные типы рёбер для расширения окрестности.
     * Исключаем структурные связи типа CONTAINS, так как они менее информативны для RAG.
     */
    private val importantEdgeKinds = setOf(
        EdgeKind.CALLS_CODE,      // Вызовы методов
        EdgeKind.DEPENDS_ON,      // Зависимости
        EdgeKind.IMPLEMENTS,       // Реализация интерфейсов
        EdgeKind.INHERITS,         // Наследование
        EdgeKind.EXTENDS,          // Расширение
        EdgeKind.OVERRIDES,        // Переопределение методов
        EdgeKind.CALLS_HTTP,       // HTTP вызовы
        EdgeKind.CALLS_GRPC,       // gRPC вызовы
        EdgeKind.READS,            // Чтение из БД
        EdgeKind.WRITES,           // Запись в БД
        EdgeKind.QUERIES,          // Запросы к БД
        EdgeKind.PRODUCES,         // Публикация в топики
        EdgeKind.CONSUMES,         // Потребление из топиков
    )

    override fun process(context: QueryProcessingContext): Boolean {
        // Пропускаем, если уже есть соседние узлы
        if (context.hasMetadata(QueryMetadataKeys.NEIGHBOR_NODES)) {
            return true
        }

        // Получаем найденные узлы из точного поиска
        val exactNodes = context.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
            ?: return true // Если нет точных узлов, пропускаем

        val nodes = exactNodes.filterIsInstance<Node>()
        if (nodes.isEmpty()) {
            log.debug("Нет узлов для расширения окрестности")
            return true
        }

        try {
            val neighborNodes = findNeighborNodes(nodes, radius = 1)
            
            if (neighborNodes.isNotEmpty()) {
                context.setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, neighborNodes)
                context.setMetadata(QueryMetadataKeys.NEIGHBOR_EXPANSION_RADIUS, 1)
                
                context.addStep(
                    ProcessingStep(
                        advisorName = getName(),
                        input = "Найдено ${nodes.size} точных узлов",
                        output = "Добавлено ${neighborNodes.size} соседних узлов",
                    ),
                )
                
                log.info("Расширена окрестность: найдено {} соседних узлов для {} исходных узлов", 
                    neighborNodes.size, nodes.size)
            } else {
                log.debug("Не найдено соседних узлов для {} исходных узлов", nodes.size)
            }
        } catch (e: Exception) {
            log.warn("Ошибка при расширении окрестности: {}", e.message, e)
            // Не прерываем цепочку, просто логируем ошибку
        }

        return true
    }

    /**
     * Находит соседние узлы для заданных узлов.
     * @param seedNodes исходные узлы
     * @param radius радиус расширения (1 = прямые соседи, 2 = соседи соседей и т.д.)
     */
    private fun findNeighborNodes(seedNodes: List<Node>, radius: Int = 1): List<Node> {
        val seedNodeIds = seedNodes.mapNotNull { it.id }.toSet()
        if (seedNodeIds.isEmpty()) {
            return emptyList()
        }

        val allNeighborIds = mutableSetOf<Long>()
        var currentLevelIds = seedNodeIds

        // Расширяем окрестность на указанный радиус
        repeat(radius) {
            val edges = findEdgesForNodes(currentLevelIds)
            
            // Собираем ID соседних узлов
            val nextLevelIds = mutableSetOf<Long>()
            edges.forEach { edge ->
                // Добавляем целевой узел, если он не в исходных узлах
                edge.dst.id?.let { dstId ->
                    if (dstId !in seedNodeIds) {
                        nextLevelIds.add(dstId)
                    }
                }
                // Добавляем исходный узел, если он не в исходных узлах (для входящих рёбер)
                edge.src.id?.let { srcId ->
                    if (srcId !in seedNodeIds) {
                        nextLevelIds.add(srcId)
                    }
                }
            }
            
            // Исключаем уже обработанные узлы
            nextLevelIds.removeAll(allNeighborIds)
            allNeighborIds.addAll(nextLevelIds)
            currentLevelIds = nextLevelIds
        }

        if (allNeighborIds.isEmpty()) {
            return emptyList()
        }

        // Загружаем соседние узлы из базы
        val neighborNodes = nodeRepository.findAllByIdIn(allNeighborIds)
        
        log.debug("Найдено {} соседних узлов для {} исходных узлов (радиус={})", 
            neighborNodes.size, seedNodes.size, radius)
        
        return neighborNodes
    }

    /**
     * Находит все рёбра для заданных узлов (исходящие и входящие).
     * Фильтрует только по важным типам рёбер.
     */
    private fun findEdgesForNodes(nodeIds: Set<Long>): List<com.bftcom.docgenerator.domain.edge.Edge> {
        if (nodeIds.isEmpty()) {
            return emptyList()
        }

        // Находим исходящие рёбра (где наши узлы - источники)
        val outgoingEdges = edgeRepository.findAllBySrcIdIn(nodeIds)
        
        // Находим входящие рёбра (где наши узлы - цели)
        val incomingEdges = edgeRepository.findAllByDstIdIn(nodeIds)
        
        // Объединяем и фильтруем по важным типам
        val allEdges = (outgoingEdges + incomingEdges)
            .filter { it.kind in importantEdgeKinds }
            .distinctBy { Triple(it.src.id, it.dst.id, it.kind) }
        
        log.debug("Найдено {} рёбер для {} узлов", allEdges.size, nodeIds.size)
        
        return allEdges
    }
}

