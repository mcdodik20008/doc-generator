package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Шаг нормализации запроса.
 * Удаляет лишние пробелы, приводит к единому формату, исправляет опечатки.
 */
@Component
class NormalizationStep : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.NORMALIZATION

    override fun execute(context: QueryProcessingContext): StepResult {
        val originalQuery = context.currentQuery
        var normalizedQuery = originalQuery

        // Нормализация пробелов
        normalizedQuery = normalizedQuery
            .replace(Regex("\\s+"), " ")
            .trim()

        // Удаление лишних знаков препинания в конце
        normalizedQuery = normalizedQuery.trimEnd('?', '!', '.', ',', ';', ':')

        val updatedContext = if (normalizedQuery != originalQuery) {
            context
                .updateQuery(normalizedQuery)
                .setMetadata(QueryMetadataKeys.NORMALIZED, true)
                .addStep(
                    ProcessingStep(
                        advisorName = "NormalizationStep",
                        input = originalQuery,
                        output = normalizedQuery,
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )
        } else {
            context
        }

        // Всегда переходим к EXTRACTION после нормализации
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.EXTRACTION,
        )
    }
}
