package com.bftcom.docgenerator.rag.api

/**
 * Enum для всех ключей метаданных запроса.
 * Используется для типобезопасного доступа к метаданным и предотвращения опечаток.
 */
enum class QueryMetadataKeys(val key: String) {
        // Переформулировка запроса
        REWRITTEN("rewritten"),
        REWRITTEN_QUERY("rewrittenQuery"),
        PREVIOUS_QUERY("previousQuery"),

        // Расширение запроса
        EXPANDED("expanded"),
        EXPANDED_QUERIES("expandedQueries"),

        // Нормализация
        NORMALIZED("normalized"),

        // Извлечение намерения
        INTENT("intent"),

        // Ключевые слова
        KEYWORDS("keywords"),

        // Метрики качества
        QUALITY_METRICS("qualityMetrics"),

        // История разговора
        CONVERSATION_HISTORY("conversationHistory"),
        HAS_HISTORY("hasHistory"),
        HISTORY_ERROR("historyError"),

        // Ошибки обработки (динамические, формат: error_<advisorName>)
        ERROR_PREFIX("error_"),

        // Точный поиск узлов
        EXACT_NODES("exactNodes"),
        EXACT_NODE_SEARCH_RESULT("exactNodeSearchResult"),

        // Расширение окрестности (соседние узлы)
        NEIGHBOR_NODES("neighborNodes"),
        NEIGHBOR_EXPANSION_RADIUS("neighborExpansionRadius"),

        // Результаты поиска по чанкам
        CHUNKS("chunks"),
        FILTERED_CHUNKS("filteredChunks"),

        // Текстовые связи графа
        GRAPH_RELATIONS_TEXT("graphRelationsText"),

        // Статус обработки
        PROCESSING_STATUS("processingStatus"),
}

