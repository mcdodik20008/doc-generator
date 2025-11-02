package com.bftcom.docgenerator.domain.node

import com.bftcom.docgenerator.model.RawUsage

/**
 * Полное описание метаданных, хранящихся в Node.meta (JSONB).
 * Используется при построении графа и при линковке (GraphLinker).
 */
data class NodeMeta(
    /** Источник ноды: onFile, onType, onFunction, onProperty и т. д. */
    val source: String? = null,
    /** Полное имя пакета, например com.bftcom.rr.uds.service.steps */
    val pkgFqn: String? = null,
    /** FQN владельца (для методов, полей, вложенных типов) */
    val ownerFqn: String? = null,
    /** Список импортов из файла */
    val imports: List<String>? = null,
    /** Список аннотаций на элементе (simple или FQN) */
    val annotations: List<String>? = null,
    /** Простые имена суpertypes (extends/implements) */
    val supertypesSimple: List<String>? = null,
    /** Разрешённые FQN суpertypes (ускоряет линковку) */
    val supertypesResolved: List<String>? = null,
    /** Сигнатура метода в сыром виде */
    val signature: String? = null,
    /** Список типов параметров */
    val paramTypes: List<String>? = null,
    /** Возвращаемый тип */
    val returnType: String? = null,
    /** Список параметров по именам (если нужно для отладки) */
    val params: List<String>? = null,
    /** Локальные переменные и их типы */
    val locals: Map<String, String>? = null,
    /** Поля класса и их типы */
    val fields: Map<String, String>? = null,
    /** Модификаторы (public, private, isSuspend и т. д.) */
    val modifiers: Map<String, Any>? = null,
    /** KDoc (summary / details / tags) */
    val kdoc: KDocMeta? = null,
    /** Прямые "сырые" вызовы, собранные анализатором */
    val rawUsages: List<RawUsage>? = null,
    /** Абсолютный путь к файлу */
    val filePath: String? = null,
    /** Номера строк в исходнике (startLine, endLine) */
    val lineRange: Pair<Int, Int>? = null,
    /** Хэш исходника (для быстрого сравнения изменений) */
    val sourceHash: String? = null,
)
