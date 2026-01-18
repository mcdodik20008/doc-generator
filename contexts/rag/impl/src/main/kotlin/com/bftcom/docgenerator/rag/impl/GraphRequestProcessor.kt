package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.steps.QueryStep
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Графовый процессор обработки запроса перед RAG.
 * Выполняет шаги как FSM, принимая решения на основе контекста.
 */
@Component
class GraphRequestProcessor(
        steps: List<QueryStep>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val registry = steps.associateBy { it.type }

        fun process(originalQuery: String, sessionId: String): QueryProcessingContext {
                var context = QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                )

                var currentStep = ProcessingStepType.NORMALIZATION
                val visitedSteps = mutableSetOf<ProcessingStepType>()

                while (true) {
                        if (currentStep == ProcessingStepType.COMPLETED || currentStep == ProcessingStepType.FAILED) {
                                return finalizeProcessing(context, currentStep)
                        }

                        if (!visitedSteps.add(currentStep)) {
                                log.warn("Обнаружен цикл в графе обработки запроса: шаг {} уже выполнялся", currentStep)
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }

                        val step = registry[currentStep]
                        if (step == null) {
                                log.warn("Не найден обработчик шага {}, прекращаем обработку", currentStep)
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }

                        val startTime = System.currentTimeMillis()
                        val result = try {
                                step.execute(context)
                        } catch (e: Exception) {
                                log.warn("Ошибка на шаге {}: {}", currentStep, e.message ?: e.javaClass.simpleName, e)
                                val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${currentStep.name}"
                                val failedContext = context.setMetadata(errorKey, e.message ?: "Unknown error")
                                return finalizeProcessing(failedContext, ProcessingStepType.FAILED)
                        }
                        val duration = System.currentTimeMillis() - startTime
                        if (duration > 10_000) {
                                log.warn("Шаг {} выполнялся слишком долго: {} мс", currentStep, duration)
                        }

                        context = result.context
                        
                        // Используем карту переходов для определения следующего шага
                        val transitions = step.getTransitions()
                        val nextStep = transitions[result.transitionKey]
                        if (nextStep == null) {
                                log.error("Не найден переход для ключа '{}' в шаге {}", result.transitionKey, currentStep)
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }
                        currentStep = nextStep
                }
        }

        private fun finalizeProcessing(
                context: QueryProcessingContext,
                status: ProcessingStepType,
        ): QueryProcessingContext {
                val withStatus = context.setMetadata(QueryMetadataKeys.PROCESSING_STATUS, status.name)
                val step = ProcessingStep(
                        advisorName = "GraphRequestProcessor",
                        input = context.originalQuery,
                        output = "Завершено со статусом $status",
                        stepType = status,
                        status = if (status == ProcessingStepType.FAILED) ProcessingStepStatus.FAILED else ProcessingStepStatus.SUCCESS,
                )
                return withStatus.addStep(step)
        }
}

