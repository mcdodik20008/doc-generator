package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.dto.CrossAppGraphResponse
import com.bftcom.docgenerator.domain.dto.IntegrationType

/**
 * Сервис для построения cross-app графов - визуализация связей между приложениями
 * через интеграционные точки (HTTP endpoints, Kafka topics, Camel routes).
 */
interface CrossAppGraphService {
    /**
     * Строит cross-app граф для указанных приложений.
     *
     * @param applicationIds Список ID приложений. Если пустой, используются все приложения.
     * @param integrationTypes Типы интеграций для фильтрации. Если пустой, используются все типы.
     * @param limit Максимальное количество интеграционных точек для включения в граф.
     * @return Cross-app граф с узлами (приложения + интеграции) и ребрами между ними.
     */
    fun buildCrossAppGraph(
        applicationIds: List<Long> = emptyList(),
        integrationTypes: Set<IntegrationType> = emptySet(),
        limit: Int = 1000,
    ): CrossAppGraphResponse
}
