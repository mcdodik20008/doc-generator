package com.bftcom.docgenerator.graph.api.model

/**
 * Универсальные ключи для [RawDecl.attributes].
 * Используются для унифицированного описания произвольных свойств,
 * чтобы не плодить "магические" строки при формировании деклараций.
 */
enum class RawAttrKey(
    val key: String,
) {
    /** Полный FQN элемента (если применимо). */
    FQN("fqn"),

    /** Сигнатура до тела / до знака '='. */
    SIGNATURE("signature"),

    /** Плоский текст KDoc без форматирования. */
    KDOC_TEXT("kdoc.text"),

    /** Разобранная структура KDoc (summary/details/tags). */
    KDOC_META("kdoc.meta"),

    /** Имя файла, если оно специфично для контекста (редко используется). */
    FILE_NAME("file.name"),

    /** Сырые импорты (список строк). */
    IMPORTS("imports"),

    /** Явные модификаторы (public/private/etc.). */
    MODIFIERS("modifiers"),

    /** Любые флаги для парсера (например, PSI body is NULL). */
    PARSER_FLAGS("parser.flags"),

    /** Временный канал для дополнительного парсинга (не сериализуется в NodeMeta напрямую). */
    INTERNAL_HINTS("internal.hints"),
}
