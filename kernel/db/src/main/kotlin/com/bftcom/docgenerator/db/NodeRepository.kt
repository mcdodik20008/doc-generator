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

    fun findAllByApplicationIdInAndKindIn(
        applicationIds: Collection<Long>,
        kinds: Set<NodeKind>,
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
     * Использует триграм индекс (migration 15_add_trigram_indexes.sql) для оптимизации LIKE запросов.
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
        pageable: Pageable = Pageable.ofSize(1000),
    ): List<Node>

    /**
     * Находит узлы по частичному FQN (например, для поиска по имени класса или метода).
     * Использует триграм индекс (migration 15_add_trigram_indexes.sql) для оптимизации LIKE запросов.
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
        pageable: Pageable = Pageable.ofSize(1000),
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
        pageable: Pageable = Pageable.ofSize(1000),
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

    /**
     * Находит узлы по имени класса (без учета регистра).
     * Используется в RAG для более гибкого поиска.
     */
    @Query(
        """
        SELECT n FROM Node n
        WHERE n.application.id = :applicationId
        AND n.kind IN :classKinds
        AND (LOWER(n.name) = LOWER(:className) OR LOWER(n.fqn) LIKE LOWER(CONCAT('%', :className, '%')))
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndClassNameIgnoreCase(
        @Param("applicationId") applicationId: Long,
        @Param("className") className: String,
        @Param("classKinds") classKinds: Set<NodeKind>,
    ): List<Node>

    /**
     * Находит узлы по имени класса и метода (без учета регистра).
     * Используется в RAG для более гибкого поиска.
     */
    @Query(
        """
        SELECT n FROM Node n
        WHERE n.application.id = :applicationId
        AND n.kind = :methodKind
        AND LOWER(n.name) = LOWER(:methodName)
        AND n.parent IS NOT NULL
        AND (LOWER(n.parent.name) = LOWER(:className) OR LOWER(n.parent.fqn) LIKE LOWER(CONCAT('%', :className, '%')))
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndClassNameAndMethodNameIgnoreCase(
        @Param("applicationId") applicationId: Long,
        @Param("className") className: String,
        @Param("methodName") methodName: String,
        @Param("methodKind") methodKind: NodeKind,
        pageable: Pageable = Pageable.ofSize(1000),
    ): List<Node>

    /**
     * Находит узлы по имени метода (без учета регистра).
     * Используется в RAG для более гибкого поиска.
     */
    @Query(
        """
        SELECT n FROM Node n
        WHERE n.application.id = :applicationId
        AND n.kind = :methodKind
        AND LOWER(n.name) = LOWER(:methodName)
        ORDER BY n.fqn
        """
    )
    fun findByApplicationIdAndMethodNameIgnoreCase(
        @Param("applicationId") applicationId: Long,
        @Param("methodName") methodName: String,
        @Param("methodKind") methodKind: NodeKind,
        pageable: Pageable = Pageable.ofSize(1000),
    ): List<Node>

    fun findAllByParentId(parentId: Long): List<Node>

    fun countByApplicationId(applicationId: Long): Long

    /**
     * Топологический выбор: находит узлы, у которых все зависимости уже задокументированы.
     * - METHOD: все METHOD-зависимости через dependency-рёбра задокументированы (самоссылки исключены)
     * - CLASS/INTERFACE/ENUM/RECORD: все METHOD+FIELD дочерние задокументированы
     * - PACKAGE: все TYPE + вложенные PACKAGE дочерние задокументированы (от листовых пакетов к корневым)
     * - MODULE/REPO: все PACKAGE + вложенные MODULE дочерние задокументированы
     * - Остальные (FIELD, ENDPOINT, TOPIC и т.д.): всегда готовы
     */
    @Query(
        value = """
            SELECT n.*
            FROM doc_generator.node n
            WHERE
              NOT EXISTS (
                SELECT 1 FROM doc_generator.node_doc d
                WHERE d.node_id = n.id AND d.locale = :locale
              )
              AND (n.kind != 'METHOD' OR NOT EXISTS (
                SELECT 1 FROM doc_generator.edge e
                JOIN doc_generator.node t ON t.id = e.dst_id
                WHERE e.src_id = n.id AND e.dst_id != n.id
                AND e.kind IN ('CALLS_CODE','THROWS','READS','WRITES','QUERIES',
                               'CALLS_HTTP','CALLS_GRPC','PRODUCES','CONSUMES','CONFIGURES',
                               'CIRCUIT_BREAKER_TO','RETRIES_TO','TIMEOUTS_TO','DEPENDS_ON')
                AND t.kind = 'METHOD'
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc dd
                  WHERE dd.node_id = t.id AND dd.locale = :locale
                )
              ))
              AND (n.kind NOT IN ('CLASS','INTERFACE','ENUM','RECORD') OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id AND c.kind IN ('METHOD','FIELD')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                )
              ))
              AND (n.kind != 'PACKAGE' OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id
                AND c.kind IN ('CLASS','INTERFACE','ENUM','RECORD','PACKAGE')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                )
              ))
              AND (n.kind NOT IN ('MODULE','REPO') OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id AND c.kind IN ('PACKAGE','MODULE')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                )
              ))
            ORDER BY n.id
            LIMIT :limit
            FOR UPDATE OF n SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockNextReadyNodesWithoutDoc(
        @Param("locale") locale: String,
        @Param("limit") limit: Int,
    ): List<Node>

    /**
     * Фоллбэк для циклических зависимостей: выбирает любой незадокументированный узел.
     * Используется когда lockNextReadyNodesWithoutDoc возвращает пусто, но незадокументированные узлы ещё есть.
     */
    @Query(
        value = """
            SELECT n.*
            FROM doc_generator.node n
            WHERE NOT EXISTS (
              SELECT 1 FROM doc_generator.node_doc d
              WHERE d.node_id = n.id AND d.locale = :locale
            )
            ORDER BY n.id
            LIMIT :limit
            FOR UPDATE OF n SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockNextAnyNodesWithoutDoc(
        @Param("locale") locale: String,
        @Param("limit") limit: Int,
    ): List<Node>
}
