package com.bftcom.docgenerator.rag.api

/**
 * Интерфейс для advisors, которые обрабатывают запрос перед RAG
 */
interface QueryProcessingAdvisor {
        /**
         * Имя advisor для логирования и метаданных
         */
        fun getName(): String

        /**
         * Обрабатывает запрос в контексте
         * @return true, если advisor обработал запрос и нужно продолжить цепочку
         */
        fun process(context: QueryProcessingContext): Boolean

        /**
         * Порядок выполнения (меньше = раньше)
         */
        fun getOrder(): Int = 0
}

