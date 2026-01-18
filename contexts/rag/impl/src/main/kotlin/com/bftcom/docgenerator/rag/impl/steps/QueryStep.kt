package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryProcessingContext

interface QueryStep {
    val type: ProcessingStepType

    /**
     * Основная логика выполнения шага
     */
    fun execute(context: QueryProcessingContext): StepResult

    /**
     * Карта переходов: какой ключ (transitionKey) ведет к какому шагу (ProcessingStepType)
     */
    fun getTransitions(): Map<String, ProcessingStepType>

}
