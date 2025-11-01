package com.bftcom.docgenerator.api.chunk.dto

data class ChunkBuildRequest(
    val applicationId: Long,
    val strategy: String, // "per-node"
    val dryRun: Boolean = false, // только посчитать, без записи
    val limitNodes: Long? = null, // лимит узлов на прогон
    val batchSize: Int = 200, // размер страницы чтения нод
    val includeKinds: Set<String>? = null, // фильтр по NodeKind.name (CLASS, METHOD, ...)
    val withEdgesRelations: Boolean = true, // подтягивать рёбра для hint'ов
)
