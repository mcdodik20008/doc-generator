package com.bftcom.docgenerator.rag.api

/**
 * Типы шагов графовой обработки запроса.
 */
enum class ProcessingStepType(val description: String) {
    NORMALIZATION("Подготовка запроса"),
    INTENT_CLASSIFICATION("Определение типа запроса"),
    EXTRACTION("Анализ сущностей"),
    HYPOTHESIS_GENERATION("Генерация гипотез"),
    EXACT_SEARCH("Поиск по сигнатуре"),
    GRAPH_EXPANSION("Сбор связей кода"),
    REWRITING("Уточнение формулировки"),
    EXPANSION("Обогащение синонимами"),

    VECTOR_SEARCH("Поиск по контексту"),
    RERANKING("Отбор лучших чанков"),

    COMPLETED("Ответ сформирован"),
    FAILED("Информации недостаточно")
}
