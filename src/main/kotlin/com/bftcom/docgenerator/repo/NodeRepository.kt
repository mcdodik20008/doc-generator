package com.bftcom.docgenerator.repo

import com.bftcom.docgenerator.domain.node.Node
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface NodeRepository : JpaRepository<Node, Long> {
    fun findByApplicationIdAndFqn(applicationId: Long, fqn: String): Optional<Node>
    fun findAllByApplicationId(applicationId: Long): List<Node>
}