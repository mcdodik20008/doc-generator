package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface EdgeRepository : JpaRepository<Edge, Long> {
    fun findAllBySrcId(srcId: Long): List<Edge>
    fun findAllByDstId(dstId: Long): List<Edge>
    fun findAllBySrcIdIn(srcIds: Set<Long>): List<Edge>
    fun findAllByDstIdIn(dstIds: Set<Long>): List<Edge>
    fun findAllBySrcIdAndDstIdIn(srcId: Long, dstIds: Set<Long>): List<Edge>
    fun findAllByDstIdAndSrcIdIn(dstId: Long, srcIds: Set<Long>): List<Edge>
}
