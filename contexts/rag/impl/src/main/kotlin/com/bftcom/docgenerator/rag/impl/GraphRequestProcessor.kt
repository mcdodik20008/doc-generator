package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.steps.QueryStep
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

    companion object {
        private const val MAX_ITERATIONS = 20
        private const val SLOW_STEP_THRESHOLD_MS = 10_000L
        private const val STEP_TIMEOUT_SECONDS = 30L
    }

        fun process(originalQuery: String, sessionId: String, applicationId: Long? = null): QueryProcessingContext {
                // Валидация входных параметров
                if (originalQuery.isBlank()) {
                    log.warn("Empty query provided to GraphRequestProcessor")
                    return QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                    ).setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.FAILED.name)
                }

                log.debug("Starting query processing: query='$originalQuery', sessionId='$sessionId', applicationId=$applicationId")

                var context = QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                )

                if (applicationId != null) {
                    context.setMetadata(QueryMetadataKeys.APPLICATION_ID, applicationId)
                }

                var currentStep = ProcessingStepType.NORMALIZATION
                val visitedSteps = mutableSetOf<ProcessingStepType>()
                var iterationCount = 0

                while (iterationCount < MAX_ITERATIONS) {
                        iterationCount++
                        if (currentStep == ProcessingStepType.COMPLETED || currentStep == ProcessingStepType.FAILED) {
                                return finalizeProcessing(context, currentStep)
                        }

                        if (!visitedSteps.add(currentStep)) {
                                log.warn("Обнаружен цикл в графе обработки запроса: шаг {} уже выполнялся. Путь: {}", currentStep, visitedSteps)
                                val cycleContext = context.setMetadata("processing_path", visitedSteps.map { it.name })
                                return finalizeProcessing(cycleContext, ProcessingStepType.FAILED)
                        }

                        val step = registry[currentStep]
                        if (step == null) {
                                log.error("Не найден обработчик шага {}. Зарегистрированные шаги: {}. Прекращаем обработку", currentStep, registry.keys)
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }

                        val startTime = System.currentTimeMillis()
                        val result = try {
                                CompletableFuture.supplyAsync {
                                    step.execute(context)
                                }.orTimeout(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).get()
                        } catch (e: TimeoutException) {
                                log.error("Шаг {} превысил timeout в {} секунд", currentStep, STEP_TIMEOUT_SECONDS)
                                val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${currentStep.name}"
                                val failedContext = context.setMetadata(errorKey, "Step timed out after ${STEP_TIMEOUT_SECONDS}s")
                                return finalizeProcessing(failedContext, ProcessingStepType.FAILED)
                        } catch (e: Exception) {
                                val cause = e.cause ?: e
                                log.error("Ошибка на шаге {}: {}", currentStep, cause.message ?: cause.javaClass.simpleName, cause)
                                val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${currentStep.name}"
                                val failedContext = context.setMetadata(errorKey, cause.message ?: "Unknown error")
                                return finalizeProcessing(failedContext, ProcessingStepType.FAILED)
                        }
                        val duration = System.currentTimeMillis() - startTime
                        if (duration > SLOW_STEP_THRESHOLD_MS) {
                                log.warn("Шаг {} выполнялся слишком долго: {} мс", currentStep, duration)
                        }

                        context = result.context

                        // Используем карту переходов для определения следующего шага
                        val transitions = step.getTransitions()
                        val nextStep = transitions[result.transitionKey]
                        if (nextStep == null) {
                                log.error("Не найден переход для ключа '{}' в шаге {}. Доступные переходы: {}", result.transitionKey, currentStep, transitions.keys)
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }
                        currentStep = nextStep
                }

                // Если достигли максимального числа итераций
                log.error("Достигнуто максимальное количество итераций ({}) при обработке запроса", MAX_ITERATIONS)
                return finalizeProcessing(context, ProcessingStepType.FAILED)
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

