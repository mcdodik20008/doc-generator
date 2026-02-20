package com.bftcom.docgenerator.analysis

import com.bftcom.docgenerator.domain.enums.NodeKind

data class ImpactAnalysisResult(
        val rootNodeId: Long,
        val totalImpactedNodes: Int,
        val maxDepthReached: Int,
        val impactedNodes: List<ImpactNode>
)

data class ImpactNode(
        val id: Long,
        val fqn: String,
        val name: String?,
        val kind: NodeKind,
        val depth: Int
)
