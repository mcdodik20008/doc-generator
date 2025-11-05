package com.bftcom.docgenerator.domain.enums

/**
 * Язык исходного артефакта.
 *
 * Возможные значения:
 * - kotlin — код на Kotlin
 * - java — код на Java
 * - sql — SQL-запросы, миграции
 * - yaml — конфигурационные файлы
 * - md — Markdown-документация
 * - other — прочие
 */
enum class Lang {
    kotlin,
    java,
    sql,
    yaml,
    md,
    other,
}
