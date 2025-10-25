package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.chunk.Chunk
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ChunkRepository : JpaRepository<Chunk, Long> {
    fun findAllByApplicationId(
        applicationId: Long,
        pageable: Pageable,
    ): Page<Chunk>

    fun findAllByNodeId(
        nodeId: Long,
        pageable: Pageable,
    ): Page<Chunk>

    fun findByApplicationIdAndContentHash(
        applicationId: Long,
        contentHash: String,
    ): Chunk?

    fun deleteByApplicationId(applicationId: Long): Long

    fun findTopByNodeIdOrderByCreatedAtDesc(nodeId: Long): Chunk?
}
