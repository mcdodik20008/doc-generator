package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.node.Node
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NodeRepository : JpaRepository<Node, Long> {
    fun findByApplicationIdAndFqn(
        applicationId: Long,
        fqn: String,
    ): Node?

    fun findAllByApplicationId(applicationId: Long, pageable: Pageable): List<Node>

    fun existsByApplicationIdAndFqn(
        applicationId: Long,
        fqn: String,
    ): Boolean
}
