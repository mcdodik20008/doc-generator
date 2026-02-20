package com.bftcom.docgenerator.analysis

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangeImpactService(
        private val edgeRepository: EdgeRepository,
        private val nodeRepository: NodeRepository
) {
    @Transactional(readOnly = true)
    fun analyzeImpact(nodeId: Long, maxDepth: Int = 5): ImpactAnalysisResult {
        val rootNode =
                nodeRepository.findById(nodeId).orElseThrow {
                    IllegalArgumentException("Node with ID $nodeId not found")
                }

        val visited = mutableSetOf<Long>(nodeId)
        val impactedNodes = mutableListOf<ImpactNode>()

        var currentLevelIds = setOf(nodeId)
        var depth = 1
        var actualMaxDepth = 0

        while (currentLevelIds.isNotEmpty() && depth <= maxDepth) {
            val edges = edgeRepository.findAllByDstIdIn(currentLevelIds)

            // Extract the IDs of the dependant nodes without triggering an N+1 query for each Node
            // proxy
            val srcIds = edges.mapNotNull { it.src.id }.filter { it !in visited }.toSet()

            if (srcIds.isEmpty()) {
                break
            }

            val nextLevelNodes = nodeRepository.findAllByIdIn(srcIds)
            actualMaxDepth = depth

            val nextLevelIds = mutableSetOf<Long>()
            for (node in nextLevelNodes) {
                visited.add(node.id!!)
                nextLevelIds.add(node.id!!)

                impactedNodes.add(
                        ImpactNode(
                                id = node.id!!,
                                fqn = node.fqn,
                                name = node.name,
                                kind = node.kind,
                                depth = depth
                        )
                )
            }

            currentLevelIds = nextLevelIds
            depth++
        }

        return ImpactAnalysisResult(
                rootNodeId = nodeId,
                totalImpactedNodes = impactedNodes.size,
                maxDepthReached = actualMaxDepth,
                impactedNodes = impactedNodes.sortedBy { it.depth }
        )
    }
}
