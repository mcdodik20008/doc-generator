package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NodeRepository : JpaRepository<Node, Long> {
    fun findByApplicationIdAndFqn(
        applicationId: Long,
        fqn: String,
    ): Node?

    fun findAllByApplicationId(
        applicationId: Long,
        pageable: Pageable,
    ): List<Node>

    fun findAllByApplicationIdAndKindIn(
        applicationId: Long,
        kinds: Set<NodeKind>,
        pageable: Pageable,
    ): List<Node>

    fun findAllByIdIn(ids: Set<Long>): List<Node>

    fun findPageAllByApplicationId(applicationId: Long, pageable: Pageable): Page<Node>

    fun findPageAllByApplicationIdAndKindIn(applicationId: Long, kind: Set<NodeKind>, pageable: Pageable): Page<Node>
}
