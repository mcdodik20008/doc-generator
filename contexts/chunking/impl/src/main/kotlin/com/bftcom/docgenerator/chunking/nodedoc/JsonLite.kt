package com.bftcom.docgenerator.chunking.nodedoc

/**
 * Минимальный JSON-сериализатор (без внешних зависимостей).
 * Поддерживает: null, String, Number, Boolean, Map<String,*>, List<*>.
 */
object JsonLite {
    fun stringify(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> quote(value)
            is Number -> value.toString()
            is Boolean -> if (value) "true" else "false"
            is Map<*, *> -> stringifyObject(value)
            is Iterable<*> -> stringifyArray(value)
            else -> quote(value.toString())
        }

    fun objectOf(map: Map<String, Any?>): String = stringifyObject(map)

    private fun stringifyObject(map: Map<*, *>): String {
        val parts =
            map.entries.joinToString(",") { (k, v) ->
                quote(k?.toString() ?: "null") + ":" + stringify(v)
            }
        return "{$parts}"
    }

    private fun stringifyArray(list: Iterable<*>): String =
        list.joinToString(prefix = "[", postfix = "]", separator = ",") { stringify(it) }

    private fun quote(s: String): String = "\"" + escape(s) + "\""

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

