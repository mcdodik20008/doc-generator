package com.bftcom.docgenerator.api.graph

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.domain.dto.CrossAppGraphResponse
import com.bftcom.docgenerator.domain.dto.IntegrationType
import com.bftcom.docgenerator.graph.api.CrossAppGraphService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST контроллер для cross-app графов.
 */
@RestController
@RequestMapping("/api/graph/cross-app")
class CrossAppGraphController(
    private val crossAppGraphService: CrossAppGraphService,
    private val applicationRepo: ApplicationRepository,
) {
    /**
     * Получить cross-app граф для указанных приложений.
     *
     * @param applicationIds Список ID приложений (опционально, по умолчанию все приложения)
     * @param integrationTypes Типы интеграций для фильтрации (HTTP, KAFKA, CAMEL)
     * @param limit Максимальное количество интеграционных точек
     */
    @GetMapping
    fun getCrossAppGraph(
        @RequestParam(required = false, defaultValue = "") applicationIds: List<Long>,
        @RequestParam(required = false, defaultValue = "") integrationTypes: List<String>,
        @RequestParam(required = false, defaultValue = "1000") limit: Int,
    ): CrossAppGraphResponse {
        val types =
            integrationTypes
                .mapNotNull { IntegrationType.fromString(it) }
                .toSet()

        return crossAppGraphService.buildCrossAppGraph(
            applicationIds = applicationIds.filter { it > 0 },
            integrationTypes = types,
            limit = limit,
        )
    }

    /**
     * Получить список приложений с информацией о доступных интеграциях.
     */
    @GetMapping("/applications")
    fun getApplicationsWithIntegrations(): List<ApplicationIntegrationInfo> {
        val apps = applicationRepo.findAll()

        return apps.map { app ->
            val appId = requireNotNull(app.id)

            // For now, return basic info - can be enhanced later with actual counts
            ApplicationIntegrationInfo(
                id = appId,
                key = app.key,
                name = app.name ?: app.key,
                description = app.description,
            )
        }
    }
}

/**
 * Информация о приложении с количеством интеграций.
 */
data class ApplicationIntegrationInfo(
    val id: Long,
    val key: String,
    val name: String,
    val description: String?,
)
