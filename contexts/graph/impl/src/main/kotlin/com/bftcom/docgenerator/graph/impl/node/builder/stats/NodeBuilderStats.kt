package com.bftcom.docgenerator.graph.impl.node.builder.stats

/**
 * Статистика операций NodeBuilder.
 */
data class NodeBuilderStats(
    val created: Int,
    val updated: Int,
    val skipped: Int,
) {
    val total: Int get() = created + updated + skipped
}

/**
 * Менеджер статистики для NodeBuilder.
 */
class NodeBuilderStatsManager {
    private var createdCount = 0
    private var updatedCount = 0
    private var skippedCount = 0

    fun incrementCreated() {
        createdCount++
    }

    fun incrementUpdated() {
        updatedCount++
    }

    fun incrementSkipped() {
        skippedCount++
    }

    fun getStats(): NodeBuilderStats = NodeBuilderStats(createdCount, updatedCount, skippedCount)

    fun reset() {
        createdCount = 0
        updatedCount = 0
        skippedCount = 0
    }
}

