package com.bftcom.docgenerator.graph.impl.config

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * Связывает INFRASTRUCTURE-ноды с кодовыми нодами, которые используют
 * соответствующие конфигурационные свойства через @Value или @ConfigurationProperties.
 */
@Component
class ConfigPropertyLinker(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val VALUE_ANNOTATION_PATTERN =
            Regex(
                """@Value\s*\(\s*".*?\$\{([^}:]+)[^}]*\}.*?"\s*\)""",
            )
        private val CONFIG_PROPERTIES_PATTERN =
            Regex(
                """@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?"([^"]+)"\s*\)""",
            )
        private val CAMEL_TO_KEBAB = Regex("([a-z])([A-Z])")
    }

    /**
     * Находит и создает связи между INFRASTRUCTURE нодами и кодом.
     * @return количество созданных рёбер
     */
    fun link(application: Application): Int {
        val appId = requireNotNull(application.id) { "Application must have an ID" }

        // 1. Загружаем все INFRASTRUCTURE-ноды
        val infraNodes =
            nodeRepo.findAllByApplicationIdAndKindIn(
                appId,
                setOf(NodeKind.INFRASTRUCTURE),
                Pageable.ofSize(10000),
            )

        if (infraNodes.isEmpty()) {
            log.debug("No INFRASTRUCTURE nodes found for app [id={}]", appId)
            return 0
        }

        log.info("Found {} INFRASTRUCTURE nodes, searching for code links...", infraNodes.size)

        // Индексируем по configPrefix для быстрого поиска
        val infraByPrefix =
            infraNodes
                .associateBy { node ->
                    (node.meta["configPrefix"] as? String) ?: ""
                }.filter { it.key.isNotBlank() }

        var edgesCreated = 0

        // 2. Ищем @Value-аннотированные ноды (поля, методы)
        edgesCreated += linkValueAnnotations(appId, infraByPrefix)

        // 3. Ищем @ConfigurationProperties классы
        edgesCreated += linkConfigurationProperties(appId, infraByPrefix)

        log.info("Created {} config-to-code edges for app [id={}]", edgesCreated, appId)
        return edgesCreated
    }

    /**
     * Находит ноды с @Value аннотацией и связывает их с INFRASTRUCTURE нодами.
     */
    private fun linkValueAnnotations(
        appId: Long,
        infraByPrefix: Map<String, Node>,
    ): Int {
        // Загружаем все ноды типа FIELD и METHOD (которые могут содержать @Value)
        val candidateNodes =
            nodeRepo.findAllByApplicationIdAndKindIn(
                appId,
                setOf(NodeKind.FIELD, NodeKind.METHOD),
                Pageable.ofSize(50000),
            )

        var count = 0

        for (node in candidateNodes) {
            val sourceCode = node.sourceCode ?: continue

            // Ищем @Value("${property.key}") в исходном коде
            VALUE_ANNOTATION_PATTERN.findAll(sourceCode).forEach { match ->
                val propertyKey = match.groupValues[1]
                val infraNode = findMatchingInfraNode(propertyKey, infraByPrefix)
                if (infraNode != null) {
                    createEdge(node, infraNode, EdgeKind.DEPENDS_ON)
                    count++
                }
            }
        }

        log.debug("Linked {} @Value fields/methods to INFRASTRUCTURE nodes", count)
        return count
    }

    /**
     * Находит @ConfigurationProperties классы и связывает с INFRASTRUCTURE нодами.
     */
    private fun linkConfigurationProperties(
        appId: Long,
        infraByPrefix: Map<String, Node>,
    ): Int {
        // Загружаем CONFIG-ноды (уже классифицированы парсером)
        val configNodes =
            nodeRepo.findAllByApplicationIdAndKindIn(
                appId,
                setOf(NodeKind.CONFIG),
                Pageable.ofSize(10000),
            )

        var count = 0

        for (node in configNodes) {
            val sourceCode = node.sourceCode ?: continue

            CONFIG_PROPERTIES_PATTERN.findAll(sourceCode).forEach { match ->
                val prefix = match.groupValues[1]
                val infraNode = findMatchingInfraNode(prefix, infraByPrefix)
                if (infraNode != null) {
                    createEdge(node, infraNode, EdgeKind.CONFIGURES)
                    count++
                }
            }
        }

        log.debug("Linked {} @ConfigurationProperties classes to INFRASTRUCTURE nodes", count)
        return count
    }

    /**
     * Ищет INFRASTRUCTURE-ноду по property key.
     * Поддерживает точное совпадение и совпадение по префиксу.
     *
     * Для `rr.ups-client.api-url` → ищем infra-ноду с configPrefix `rr.ups-client`.
     * Для `rr.ups-client` → точное совпадение.
     */
    internal fun findMatchingInfraNode(
        propertyKey: String,
        infraByPrefix: Map<String, Node>,
    ): Node? {
        // Точное совпадение
        infraByPrefix[propertyKey]?.let { return it }

        // Ищем по префиксу: ключ свойства может быть длиннее prefix
        // rr.ups-client.api-url → сматчится с prefix "rr.ups-client"
        for ((prefix, node) in infraByPrefix) {
            if (propertyKey.startsWith("$prefix.")) {
                return node
            }
        }

        return null
    }

    private fun createEdge(
        src: Node,
        dst: Node,
        kind: EdgeKind,
    ) {
        val srcId = src.id ?: return
        val dstId = dst.id ?: return

        try {
            edgeRepo.upsert(srcId, dstId, kind.name)
        } catch (e: Exception) {
            log.debug("Failed to create edge {}->{} ({}): {}", srcId, dstId, kind, e.message)
        }
    }
}
