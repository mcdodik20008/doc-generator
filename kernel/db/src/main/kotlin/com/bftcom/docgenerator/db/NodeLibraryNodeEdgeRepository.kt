package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.edge.NodeLibraryNodeEdge
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NodeLibraryNodeEdgeRepository : JpaRepository<NodeLibraryNodeEdge, Long> {
    fun findAllByNodeId(nodeId: Long): List<NodeLibraryNodeEdge>

    fun findAllByLibraryNodeId(libraryNodeId: Long): List<NodeLibraryNodeEdge>

    @Modifying
    @Query(
        value = """
            insert into doc_generator.node_library_node_edge (node_id, library_node_id, kind)
            values (:nodeId, :libraryNodeId, cast(:kind as doc_generator.edge_kind))
            on conflict on constraint pk_node_library_node_edge do nothing
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("nodeId") nodeId: Long,
        @Param("libraryNodeId") libraryNodeId: Long,
        @Param("kind") kind: String,
    )
}

