package com.bftcom.docgenerator.domain.enums

/**
 * Семантика ребра графа.
 *
 * - CALLS — src вызывает dst
 * - READS / WRITES — операции ввода/вывода
 * - QUERIES — выполняет SQL-запрос
 * - PUBLISHES / CONSUMES — pub/sub взаимодействие
 * - THROWS — генерирует исключение
 * - IMPLEMENTS / OVERRIDES — реализация / переопределение
 * - LOCKS — захватывает блокировку
 * - OPENTELEMETRY — связь по трейсам
 * - USES_FEATURE — обращается к фича-тогглу
 * - DEPENDS_ON — зависимость общего типа
 */
enum class EdgeKind {
    CALLS,
    READS,
    WRITES,
    QUERIES,
    PUBLISHES,
    CONSUMES,
    THROWS,
    IMPLEMENTS,
    OVERRIDES,
    LOCKS,
    OPENTELEMETRY,
    USES_FEATURE,
    CONTAINS,
    DEPENDS_ON,
    INHERITS,
    ANNOTATED_WITH,
}
