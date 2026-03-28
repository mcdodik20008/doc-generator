package com.bftcom.docgenerator.api.analysis

import com.bftcom.docgenerator.analysis.ChangeImpactService
import com.bftcom.docgenerator.analysis.ImpactAnalysisResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** REST API для модулей аналитики. */
@RestController
@RequestMapping("/api/analysis")
class AnalysisController(
    private val changeImpactService: ChangeImpactService,
) {
    /**
     * Оценка последствий изменений компонента. Возвращает список всех узлов, зависящих от
     * переданного узла (nodeId).
     */
    @GetMapping("/impact")
    fun analyzeImpact(
        @RequestParam nodeId: Long,
        @RequestParam(defaultValue = "5") maxDepth: Int,
    ): ImpactAnalysisResult = changeImpactService.analyzeImpact(nodeId, maxDepth)
}
