package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    fun findPageAllByApplicationId(
        applicationId: Long,
        pageable: Pageable,
    ): Page<Node>

    fun findPageAllByApplicationIdAndKindIn(
        applicationId: Long,
        kind: Set<NodeKind>,
        pageable: Pageable,
    ): Page<Node>

    /**
     * Находит узлы по имени класса и имени метода.
     * Ищет метод с указанным именем, у которого родительский класс содержит указанное имя класса.
     */
    @Query(
        """
        SELECT n FROM Node n 
        WHERE n.application.id = :applicationId
        AND n.kind = :methodKind
        AND n.name = :methodName
        AND n.parent IS NOT NULL
        AND (n.parent.name = :className OR n.parent.fqn LIKE CONCAT('%', :className, '%'))
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndClassNameAndMethodName(
        @Param("applicationId") applicationId: Long,
        @Param("className") className: String,
        @Param("methodName") methodName: String,
        @Param("methodKind") methodKind: NodeKind,
    ): List<Node>

    /**
     * Находит узлы по частичному FQN (например, для поиска по имени класса или метода).
     */
    @Query(
        """
        SELECT n FROM Node n 
        WHERE n.application.id = :applicationId
        AND n.fqn LIKE CONCAT('%', :fqnPattern, '%')
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndFqnContaining(
        @Param("applicationId") applicationId: Long,
        @Param("fqnPattern") fqnPattern: String,
    ): List<Node>

    /**
     * Находит узлы по имени метода (без привязки к классу).
     */
    @Query(
        """
        SELECT n FROM Node n 
        WHERE n.application.id = :applicationId
        AND n.kind = :methodKind
        AND n.name = :methodName
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndMethodName(
        @Param("applicationId") applicationId: Long,
        @Param("methodName") methodName: String,
        @Param("methodKind") methodKind: NodeKind,
    ): List<Node>

    /**
     * Находит узлы по имени класса.
     */
    @Query(
        """
        SELECT n FROM Node n 
        WHERE n.application.id = :applicationId
        AND n.kind IN :classKinds
        AND (n.name = :className OR n.fqn LIKE CONCAT('%', :className, '%'))
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndClassName(
        @Param("applicationId") applicationId: Long,
        @Param("className") className: String,
        @Param("classKinds") classKinds: Set<NodeKind>,
    ): List<Node>
}
