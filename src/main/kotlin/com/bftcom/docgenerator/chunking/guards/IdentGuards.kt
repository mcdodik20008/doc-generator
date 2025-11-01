package com.bftcom.docgenerator.chunking.guards

import java.lang.Character.UnicodeScript
import java.text.Normalizer

object IdentGuards {
    private val ZERO_WIDTH = Regex("[\u200B\u200C\u200D\u2060\uFEFF]") // ZW chars
    private val CONTROL = Regex("\\p{Cc}") // control chars
    private val SPACE_OTHER = Regex("[\\p{Z}&&[^\\u0020]]") // not ASCII space
    private val NON_ASCII_DOT = Regex("[\uFF0E\u2024\u22C5]") // fullwidth dot & friends

    /** Валиден ли FQN: сегменты разделены '.', каждый — корректный Java/Kotlin идентификатор */
    fun isValidFqn(fqn: String): Boolean {
        if (fqn.isBlank()) return false
        if (fqn.contains("..")) return false
        val normalized = normalizeFqn(fqn)
        val parts = normalized.split('.')
        if (parts.any { it.isBlank() }) return false
        return parts.all { isValidIdentifier(it) }
    }

    /** Находит нелегальные символы (кроме [A-Za-z0-9_ .]) и спец-случаи */
    fun findIllegalChars(fqn: String): List<Int> {
        val s = fqn
        val bad = mutableListOf<Int>()
        for (cp in s.codePoints().toArray()) {
            val ch = cp.toChar()
            val ok =
                ch == '.' || ch == '_' ||
                    ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9'
            if (!ok) bad += cp
        }
        // Дополнительно: zero-width, control, non-ascii dot
        if (ZERO_WIDTH.containsMatchIn(s)) bad += 0x200B
        if (CONTROL.containsMatchIn(s)) bad += 0x0001
        if (SPACE_OTHER.containsMatchIn(s)) bad += 0x00A0
        if (NON_ASCII_DOT.containsMatchIn(s)) bad += 0xFF0E
        return bad.distinct()
    }

    /** Содержит ли токен смешанные скрипты (латиница+кириллица и т.п.) */
    fun hasMixedScripts(token: String): Boolean {
        val scripts =
            token
                .codePoints()
                .mapToObj { UnicodeScript.of(it) }
                .filter { it != UnicodeScript.COMMON && it != UnicodeScript.INHERITED }
                .toList()
                .toSet()
        return scripts.size > 1
    }

    /** Нормализация: NFKC, заменяем «псевдоточки», убираем zero-width/controls */
    fun normalizeFqn(fqn: String): String {
        val nfkc = Normalizer.normalize(fqn, Normalizer.Form.NFKC)
        return nfkc
            .replace(NON_ASCII_DOT, ".")
            .replace(ZERO_WIDTH, "")
            .replace(CONTROL, "")
            .replace(SPACE_OTHER, " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Мягкий санитайз: всё невалидное → '_' (кроме точки и ASCII), плюс схлопываем повторные '_' */
    fun sanitizeFqn(fqn: String): String {
        val n = normalizeFqn(fqn)
        val b = StringBuilder(n.length)
        for (cp in n.codePoints().toArray()) {
            val ch = cp.toChar()
            val keep =
                ch == '.' || ch == '_' ||
                    ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9'
            b.append(if (keep) ch else '_')
        }
        return b
            .toString()
            .replace(Regex("_+"), "_")
            .replace(Regex("\\._"), ".")
            .replace(Regex("_\\."), ".")
            .trim('.')
    }

    private fun isValidIdentifier(id: String): Boolean {
        if (id.isEmpty()) return false
        val first = id.codePointAt(0)
        if (!Character.isJavaIdentifierStart(first)) return false
        var i = Character.charCount(first)
        while (i < id.length) {
            val cp = id.codePointAt(i)
            if (!Character.isJavaIdentifierPart(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }
}
