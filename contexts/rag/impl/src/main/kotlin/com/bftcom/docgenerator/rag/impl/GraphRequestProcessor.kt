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

    companion object {
        private const val MAX_ITERATIONS = 20
        private const val SLOW_STEP_THRESHOLD_MS = 10_000L
    }

        fun process(originalQuery: String, sessionId: String): QueryProcessingContext {
                // Валидация входных параметров
                if (originalQuery.isBlank()) {
                    log.warn("Empty query provided to GraphRequestProcessor")
                    return QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                    ).setMetadata(QueryMetadataKeys.PROCESSING_STATUS, ProcessingStepType.FAILED.name)
                }

                log.debug("Starting query processing: query='$originalQuery', sessionId='$sessionId'")

                var context = QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = originalQuery,
                        sessionId = sessionId,
                )

                var currentStep = ProcessingStepType.NORMALIZATION
                val visitedSteps = mutableSetOf<ProcessingStepType>()
                var iterationCount = 0

                while (iterationCount < MAX_ITERATIONS) {
                        iterationCount++
                        if (currentStep == ProcessingStepType.COMPLETED || currentStep == ProcessingStepType.FAILED) {
                                return finalizeProcessing(context, currentStep)
                        }

                        // TODO: Проверка на цикл хорошая, но не логирует полный путь цикла для отладки
                        if (!visitedSteps.add(currentStep)) {
                                log.warn("Обнаружен цикл в графе обработки запроса: шаг {} уже выполнялся", currentStep)
                                // TODO: Добавить в metadata информацию о пути выполнения для отладки
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }

                        val step = registry[currentStep]
                        if (step == null) {
                                log.warn("Не найден обработчик шага {}, прекращаем обработку", currentStep)
                                // TODO: Это конфигурационная ошибка, должна быть обработана при старте приложения, а не в runtime
                                return finalizeProcessing(context, ProcessingStepType.FAILED)
                        }

                        val startTime = System.currentTimeMillis()
                        val result = try {
                                // TODO: Нет timeout для выполнения шага - шаг может зависнуть навсегда
                                step.execute(context)
                        } catch (e: Exception) {
                                // TODO: Логирование с уровнем warn может быть недостаточно для критических ошибок
                                // TODO: Stack trace логируется, но не сохраняется в контекст для пользователя
                                log.warn("Ошибка на шаге {}: {}", currentStep, e.message ?: e.javaClass.simpleName, e)
                                val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${currentStep.name}"
                                val failedContext = context.setMetadata(errorKey, e.message ?: "Unknown error")
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
                                // TODO: Это конфигурационная ошибка - должна быть выявлена при запуске через валидацию графа
                                // TODO: Логировать все доступные переходы для отладки
                                log.error("Не найден переход для ключа '{}' в шаге {}", result.transitionKey, currentStep)
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

