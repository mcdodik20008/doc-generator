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
        """,
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
        """,
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
        """,
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
        """,
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
        """,
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
        """,
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
        """,
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
     * Порядок обработки bottom-up: листья → METHOD → TYPE → PACKAGE → MODULE/REPO.
     * - Листья (FIELD, ENDPOINT, TOPIC и т.д.): всегда готовы, обрабатываются первыми
     * - METHOD: все METHOD-зависимости через dependency-рёбра задокументированы (самоссылки исключены)
     * - CLASS/INTERFACE/ENUM/RECORD: все METHOD+FIELD дочерние задокументированы
     * - PACKAGE: все TYPE + вложенные PACKAGE дочерние задокументированы (от листовых пакетов к корневым)
     * - MODULE/REPO: все PACKAGE + вложенные MODULE дочерние задокументированы
     * Зависимость считается задокументированной только при наличии непустого doc_digest.
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
                    AND dd.doc_digest IS NOT NULL AND dd.doc_digest != ''
                )
              ))
              AND (n.kind NOT IN ('CLASS','INTERFACE','ENUM','RECORD') OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id AND c.kind IN ('METHOD','FIELD')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                    AND cd.doc_digest IS NOT NULL AND cd.doc_digest != ''
                )
              ))
              AND (n.kind != 'PACKAGE' OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id
                AND c.kind IN ('CLASS','INTERFACE','ENUM','RECORD','PACKAGE')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                    AND cd.doc_digest IS NOT NULL AND cd.doc_digest != ''
                )
              ))
              AND (n.kind NOT IN ('MODULE','REPO') OR NOT EXISTS (
                SELECT 1 FROM doc_generator.node c
                WHERE c.parent_id = n.id AND c.kind IN ('PACKAGE','MODULE')
                AND NOT EXISTS (
                  SELECT 1 FROM doc_generator.node_doc cd
                  WHERE cd.node_id = c.id AND cd.locale = :locale
                    AND cd.doc_digest IS NOT NULL AND cd.doc_digest != ''
                )
              ))
            ORDER BY
              CASE n.kind
                WHEN 'FIELD'           THEN 0
                WHEN 'ENDPOINT'        THEN 0
                WHEN 'TOPIC'           THEN 0
                WHEN 'INFRASTRUCTURE'  THEN 0
                WHEN 'METHOD'    THEN 1
                WHEN 'CLASS'     THEN 2
                WHEN 'INTERFACE' THEN 2
                WHEN 'ENUM'      THEN 2
                WHEN 'RECORD'    THEN 2
                WHEN 'SERVICE'   THEN 2
                WHEN 'MAPPER'    THEN 2
                WHEN 'CONFIG'    THEN 2
                WHEN 'SCHEMA'    THEN 2
                WHEN 'EXCEPTION' THEN 2
                WHEN 'TEST'      THEN 2
                WHEN 'PACKAGE'   THEN 3
                WHEN 'MODULE'    THEN 4
                WHEN 'REPO'      THEN 5
                ELSE 6
              END,
              n.id
            LIMIT :limit
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
     * Порядок bottom-up: листья обрабатываются первыми для лучшего качества контекста.
     */
    @Query(
        value = """
            SELECT n.*
            FROM doc_generator.node n
            WHERE NOT EXISTS (
              SELECT 1 FROM doc_generator.node_doc d
              WHERE d.node_id = n.id AND d.locale = :locale
            )
            ORDER BY
              CASE n.kind
                WHEN 'FIELD'           THEN 0
                WHEN 'ENDPOINT'        THEN 0
                WHEN 'TOPIC'           THEN 0
                WHEN 'INFRASTRUCTURE'  THEN 0
                WHEN 'METHOD'    THEN 1
                WHEN 'CLASS'     THEN 2
                WHEN 'INTERFACE' THEN 2
                WHEN 'ENUM'      THEN 2
                WHEN 'RECORD'    THEN 2
                WHEN 'SERVICE'   THEN 2
                WHEN 'MAPPER'    THEN 2
                WHEN 'CONFIG'    THEN 2
                WHEN 'SCHEMA'    THEN 2
                WHEN 'EXCEPTION' THEN 2
                WHEN 'TEST'      THEN 2
                WHEN 'PACKAGE'   THEN 3
                WHEN 'MODULE'    THEN 4
                WHEN 'REPO'      THEN 5
                ELSE 6
              END,
              n.id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun lockNextAnyNodesWithoutDoc(
        @Param("locale") locale: String,
        @Param("limit") limit: Int,
    ): List<Node>
}
