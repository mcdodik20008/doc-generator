package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.nodedoc.NodeDoc
import com.bftcom.docgenerator.domain.nodedoc.NodeDocId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface NodeDocRepository : JpaRepository<NodeDoc, NodeDocId> {
    fun findByNodeIdAndLocale(
        nodeId: Long,
        locale: String,
    ): Optional<NodeDoc>

    fun findAllByNodeId(nodeId: Long): List<NodeDoc>

    fun existsByNodeIdAndLocale(
        nodeId: Long,
        locale: String,
    ): Boolean
}
