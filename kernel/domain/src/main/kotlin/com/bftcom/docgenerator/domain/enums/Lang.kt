package com.bftcom.docgenerator.domain.enums

/**
 * Язык исходного артефакта.
 *
 * Возможные значения:
 * - kotlin — код на Kotlin
 * - java — код на Java
 * - proto — Protocol Buffers (.proto)
 * - sql — SQL-запросы, миграции
 * - yaml — конфигурационные файлы
 * - md — Markdown-документация
 * - other — прочие
 */
enum class Lang {
    kotlin,
    java,
    proto,
    sql,
    yaml,
    md,
    other,
}
