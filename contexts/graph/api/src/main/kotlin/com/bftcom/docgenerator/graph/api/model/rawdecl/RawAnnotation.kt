package com.bftcom.docgenerator.graph.api.model.rawdecl

/**
 * Структурированное представление аннотации из исходного кода.
 *
 * Содержит имя, опциональный FQN и распарсенные параметры.
 * Предоставляет удобные методы доступа к типичным значениям.
 */
data class RawAnnotation(
    /** Короткое имя аннотации (например, "GetMapping"). */
    val name: String,
    /** Полное квалифицированное имя, если удалось разрешить. */
    val fqn: String? = null,
    /** Параметры аннотации: ключ — имя параметра, значение — String, List, Boolean, и т.д. */
    val params: Map<String, Any> = emptyMap(),
) {
    /** Получить строковый параметр по ключу. */
    fun getString(key: String): String? = params[key] as? String

    /** Получить массив строк по ключу. */
    @Suppress("UNCHECKED_CAST")
    fun getStringArray(key: String): List<String>? {
        return when (val v = params[key]) {
            is List<*> -> v.filterIsInstance<String>()
            is String -> listOf(v)
            else -> null
        }
    }

    /** Получить значение "value" или первого позиционного аргумента. */
    fun value(): String? = getString("value") ?: getString("_positional_0")
}
