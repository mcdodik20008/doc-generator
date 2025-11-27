package com.bftcom.docgenerator.graph.impl.linker.virtual

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Фабрика для создания виртуальных узлов (ENDPOINT, TOPIC).
 * Виртуальные узлы создаются для интеграционных точек и не имеют исходного кода.
 */
@Component
class VirtualNodeFactory(
    private val nodeRepo: NodeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Создает или находит узел ENDPOINT для указанного URL.
     * @return Пара (узел, был ли создан новый узел)
     */
    fun getOrCreateEndpointNode(
        url: String,
        httpMethod: String?,
        index: NodeIndex,
        application: Application,
    ): Pair<Node?, Boolean> {
        // Создаем FQN для endpoint: "endpoint://{httpMethod} {url}"
        val endpointFqn =
            if (httpMethod != null) {
                "endpoint://$httpMethod $url"
            } else {
                "endpoint://$url"
            }

        // Пытаемся найти существующий узел
        val existing = index.findByFqn(endpointFqn)
        if (existing != null) {
            return Pair(existing, false)
        }

        // Создаем новый узел ENDPOINT
        try {
            val endpointName = url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: url
            val endpointNode =
                nodeRepo.save(
                    Node(
                        application = application,
                        fqn = endpointFqn,
                        name = endpointName,
                        packageName = null,
                        kind = NodeKind.ENDPOINT,
                        lang = Lang.java, // виртуальный узел
                        parent = null,
                        filePath = null,
                        lineStart = null,
                        lineEnd = null,
                        sourceCode = null,
                        docComment = null,
                        signature = null,
                        codeHash = null,
                        meta =
                            mapOf(
                                "url" to url,
                                "httpMethod" to (httpMethod ?: "UNKNOWN"),
                                "source" to "library_analysis",
                            ),
                    ),
                )
            log.debug("Created ENDPOINT node: {}", endpointFqn)
            return Pair(endpointNode, true)
        } catch (e: Exception) {
            log.warn("Failed to create ENDPOINT node {}: {}", endpointFqn, e.message)
            return Pair(null, false)
        }
    }

    /**
     * Создает или находит узел TOPIC для указанного Kafka topic.
     * @return Пара (узел, был ли создан новый узел)
     */
    fun getOrCreateTopicNode(
        topic: String,
        index: NodeIndex,
        application: Application,
    ): Pair<Node?, Boolean> {
        val topicFqn = "topic://$topic"

        val existing = index.findByFqn(topicFqn)
        if (existing != null) {
            return Pair(existing, false)
        }

        // Создаем новый узел TOPIC
        try {
            val topicNode =
                nodeRepo.save(
                    Node(
                        application = application,
                        fqn = topicFqn,
                        name = topic,
                        packageName = null,
                        kind = NodeKind.TOPIC,
                        lang = Lang.java, // виртуальный узел
                        parent = null,
                        filePath = null,
                        lineStart = null,
                        lineEnd = null,
                        sourceCode = null,
                        docComment = null,
                        signature = null,
                        codeHash = null,
                        meta =
                            mapOf(
                                "topic" to topic,
                                "source" to "library_analysis",
                            ),
                    ),
                )
            log.debug("Created TOPIC node: {}", topicFqn)
            return Pair(topicNode, true)
        } catch (e: Exception) {
            log.warn("Failed to create TOPIC node {}: {}", topicFqn, e.message)
            return Pair(null, false)
        }
    }
}

