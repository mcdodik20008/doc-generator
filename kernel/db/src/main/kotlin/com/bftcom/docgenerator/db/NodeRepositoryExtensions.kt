package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.node.Node

private const val BATCH_SIZE = 1000

/**
 * Batch-вариант findAllByIdIn для больших наборов ID.
 * Разбивает ids на чанки по [BATCH_SIZE] для предотвращения проблем с размером IN-запроса.
 */
fun NodeRepository.findAllByIdInBatched(ids: Set<Long>): List<Node> {
    if (ids.size <= BATCH_SIZE) {
        return findAllByIdIn(ids)
    }
    return ids.chunked(BATCH_SIZE)
        .flatMap { batch -> findAllByIdIn(batch.toSet()) }
}
