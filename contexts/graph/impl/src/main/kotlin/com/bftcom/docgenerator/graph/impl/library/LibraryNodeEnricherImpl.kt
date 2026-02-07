package com.bftcom.docgenerator.graph.impl.library

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.api.library.LibraryNodeEnricher
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Реализация обогащения Node информацией из библиотек.
 */
@Component
class LibraryNodeEnricherImpl(
    private val libraryNodeIndex: LibraryNodeIndex,
    private val integrationPointService: IntegrationPointService,
) : LibraryNodeEnricher {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    override fun enrichNodeMeta(
        node: Node,
        meta: NodeMeta,
    ): NodeMeta {
        // Обогащаем метаданные только для методов
        if (node.kind.name != "METHOD") {
            return meta
        }

        val usages = meta.rawUsages ?: return meta
        val imports = meta.imports ?: emptyList()
        val ownerFqn = meta.ownerFqn
        val pkg = node.packageName.orEmpty()

        val integrationPoints = mutableListOf<Map<String, Any>>()
        val httpEndpoints = mutableSetOf<String>()
        val kafkaTopics = mutableSetOf<String>()
        val camelUris = mutableSetOf<String>()
        var hasRetry = false
        var hasTimeout = false
        var hasCircuitBreaker = false

        // Анализируем вызовы методов
        usages.forEach { u ->
            val libraryMethodFqn =
                when (u) {
                    is RawUsage.Simple -> {
                        if (ownerFqn != null) {
                            "$ownerFqn.${u.name}"
                        } else {
                            imports.firstOrNull { it.endsWith(".${u.name}") }?.let { "$it.${u.name}" }
                                ?: if (u.name.contains('.')) u.name else null
                        }
                    }
                    is RawUsage.Dot -> {
                        // Для Dot usage нужно разрешить receiver
                        // Упрощенная версия - используем ownerFqn
                        ownerFqn?.let { "$it.${u.member}" }
                    }
                }

            if (libraryMethodFqn != null) {
                val libraryNode = libraryNodeIndex.findByMethodFqn(libraryMethodFqn)
                if (libraryNode != null) {
                    val points = integrationPointService.extractIntegrationPoints(libraryNode)

                    for (point in points) {
                        when (point) {
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.HttpEndpoint -> {
                                point.url?.let { httpEndpoints.add(it) }
                                if (point.hasRetry) hasRetry = true
                                if (point.hasTimeout) hasTimeout = true
                                if (point.hasCircuitBreaker) hasCircuitBreaker = true

                                integrationPoints.add(
                                    mapOf(
                                        "type" to "HTTP",
                                        "url" to (point.url ?: ""),
                                        "method" to (point.httpMethod ?: ""),
                                        "clientType" to (point.clientType ?: ""),
                                    ),
                                )
                            }
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.KafkaTopic -> {
                                point.topic?.let { kafkaTopics.add(it) }
                                integrationPoints.add(
                                    mapOf(
                                        "type" to "KAFKA",
                                        "topic" to (point.topic ?: ""),
                                        "operation" to point.operation,
                                        "clientType" to (point.clientType ?: ""),
                                    ),
                                )
                            }
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.CamelRoute -> {
                                point.uri?.let { camelUris.add(it) }
                                integrationPoints.add(
                                    mapOf(
                                        "type" to "CAMEL",
                                        "uri" to (point.uri ?: ""),
                                        "endpointType" to (point.endpointType ?: ""),
                                        "direction" to point.direction,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Если нашли интеграционные точки, обогащаем метаданные
        if (integrationPoints.isNotEmpty() || httpEndpoints.isNotEmpty() || kafkaTopics.isNotEmpty() || camelUris.isNotEmpty()) {
            log.debug(
                "Found integration points for method {}: {} HTTP, {} Kafka, {} Camel",
                node.fqn,
                httpEndpoints.size,
                kafkaTopics.size,
                camelUris.size,
            )

            return meta.copy(
                libraryIntegration = mapOf(
                    "integrationPoints" to integrationPoints,
                    "httpEndpoints" to httpEndpoints.toList(),
                    "kafkaTopics" to kafkaTopics.toList(),
                    "camelUris" to camelUris.toList(),
                    "hasRetry" to hasRetry,
                    "hasTimeout" to hasTimeout,
                    "hasCircuitBreaker" to hasCircuitBreaker,
                ),
            )
        }

        return meta
    }
}
