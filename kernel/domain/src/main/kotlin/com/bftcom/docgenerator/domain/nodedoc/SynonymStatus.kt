package com.bftcom.docgenerator.domain.nodedoc

/**
 * Статус обработки узла для словаря синонимов.
 */
enum class SynonymStatus {
    /**
     * Ожидает первичной обработки.
     */
    PENDING,

    /**
     * В работе у джобы.
     */
    PROCESSING,

    /**
     * Успешно извлечены и сохранены синонимы.
     */
    INDEXED,

    /**
     * Отсеяно эвристиками (тех. названия, стоп-слова).
     */
    SKIPPED_HEURISTIC,

    /**
     * Отсеяно LLM-судьей (отсутствие бизнес-логики).
     */
    SKIPPED_JUDGE,

    /**
     * Ошибка экстракции или пустой ответ модели.
     */
    FAILED_LLM
}
