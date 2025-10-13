package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface EdgeRepository : JpaRepository<Edge, Long> {
    fun findAllBySrcId(srcId: Long): List<Edge>

    fun findAllByDstId(dstId: Long): List<Edge>

    fun findAllByKind(kind: EdgeKind): List<Edge>

    fun existsBySrcIdAndDstIdAndKind(
        srcId: Long,
        dstId: Long,
        kind: EdgeKind,
    ): Boolean

    @Modifying
    @Query("delete from Edge e where e.src.id = :srcId and e.kind in :kinds")
    fun deleteOutgoingOfKinds(
        srcId: Long,
        kinds: Collection<EdgeKind>,
    )
}
