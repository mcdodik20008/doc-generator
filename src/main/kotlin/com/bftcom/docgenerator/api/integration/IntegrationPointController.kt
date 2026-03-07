package com.bftcom.docgenerator.api.integration

import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST API для работы с интеграционными точками.
 */
@RestController
@RequestMapping("/api/integration")
class IntegrationPointController(
    private val integrationPointService: IntegrationPointService,
) {
    /**
     * Найти все методы, которые вызывают указанный URL.
     */
    @GetMapping("/methods/by-url")
    fun findMethodsByUrl(
        @RequestParam url: String,
        @RequestParam(required = false) libraryId: Long?,
    ): List<MethodInfo> =
        integrationPointService
            .findMethodsByUrl(url, libraryId)
            .map { MethodInfo.fromLibraryNode(it) }

    /**
     * Найти все методы, которые используют указанный Kafka topic.
     */
    @GetMapping("/methods/by-kafka-topic")
    fun findMethodsByKafkaTopic(
        @RequestParam topic: String,
        @RequestParam(required = false) libraryId: Long?,
    ): List<MethodInfo> =
        integrationPointService
            .findMethodsByKafkaTopic(topic, libraryId)
            .map { MethodInfo.fromLibraryNode(it) }

    /**
     * Найти все методы, которые используют указанный Camel URI.
     */
    @GetMapping("/methods/by-camel-uri")
    fun findMethodsByCamelUri(
        @RequestParam uri: String,
        @RequestParam(required = false) libraryId: Long?,
    ): List<MethodInfo> =
        integrationPointService
            .findMethodsByCamelUri(uri, libraryId)
            .map { MethodInfo.fromLibraryNode(it) }

    /**
     * Получить сводку по интеграционным точкам для метода.
     */
    @GetMapping("/method/summary")
    fun getMethodSummary(
        @RequestParam methodFqn: String,
        @RequestParam libraryId: Long,
    ): IntegrationPointService.IntegrationMethodSummary? = integrationPointService.getMethodIntegrationSummary(methodFqn, libraryId)

    /**
     * Найти все родительские клиенты в библиотеке.
     */
    @GetMapping("/parent-clients")
    fun findParentClients(
        @RequestParam libraryId: Long,
    ): List<MethodInfo> =
        integrationPointService
            .findParentClients(libraryId)
            .map { MethodInfo.fromLibraryNode(it) }

    /**
     * Информация о методе для API.
     */
    data class MethodInfo(
        val fqn: String,
        val name: String?,
        val libraryCoordinate: String,
        val isParentClient: Boolean,
    ) {
        companion object {
            fun fromLibraryNode(node: LibraryNode): MethodInfo {
                val integrationMeta = (node.meta["integrationAnalysis"] as? Map<String, Any>)
                return MethodInfo(
                    fqn = node.fqn,
                    name = node.name,
                    libraryCoordinate = node.library.coordinate,
                    isParentClient = integrationMeta?.get("isParentClient") as? Boolean ?: false,
                )
            }
        }
    }
}
