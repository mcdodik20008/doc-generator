package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import org.springframework.data.jpa.repository.JpaRepository

interface EdgeRepository : JpaRepository<Edge, Long> {

    fun findAllBySrcId(srcId: Long): List<Edge>

    fun findAllByDstId(dstId: Long): List<Edge>

    fun findAllByKind(kind: EdgeKind): List<Edge>

    fun existsBySrcIdAndDstIdAndKind(srcId: Long, dstId: Long, kind: EdgeKind): Boolean
}
