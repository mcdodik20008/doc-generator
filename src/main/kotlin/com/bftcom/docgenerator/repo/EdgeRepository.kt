package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.edge.Edge
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EdgeRepository : JpaRepository<Edge, Long> {
    fun findAllBySrcId(srcId: Long): List<Edge>

    fun findAllByDstId(dstId: Long): List<Edge>

    fun findAllBySrcIdIn(srcIds: Set<Long>): List<Edge>

    fun findAllByDstIdIn(dstIds: Set<Long>): List<Edge>

    fun findAllBySrcIdAndDstIdIn(
        srcId: Long,
        dstIds: Set<Long>,
    ): List<Edge>

    fun findAllByDstIdAndSrcIdIn(
        dstId: Long,
        srcIds: Set<Long>,
    ): List<Edge>

    @Modifying
    @Query(
        value = """
            insert into doc_generator.edge (src_id, dst_id, kind)
            values (:srcId, :dstId, cast(:kind as doc_generator.edge_kind))
            on conflict on constraint uq_edge do nothing
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("srcId") srcId: Long,
        @Param("dstId") dstId: Long,
        @Param("kind") kind: String,
    )
}
