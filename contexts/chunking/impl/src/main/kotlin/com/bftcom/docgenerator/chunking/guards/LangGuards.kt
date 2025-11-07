package com.bftcom.docgenerator.chunking.guards

object LangGuards {
    private val CYRILLIC_RE = Regex("\\p{IsCyrillic}")
    private val LATIN_RE = Regex("[A-Za-z]")
    private val LETTER_RE = Regex("\\p{L}")

    fun hasCyrillic(s: String): Boolean = CYRILLIC_RE.containsMatchIn(s)

    fun hasLatin(s: String): Boolean = LATIN_RE.containsMatchIn(s)

    /** Доля кириллицы среди букв (0.0..1.0) */
    fun cyrillicRatio(s: String): Double {
        val letters = LETTER_RE.findAll(s).count().toDouble()
        if (letters == 0.0) return 0.0
        val cyr = CYRILLIC_RE.findAll(s).count().toDouble()
        return cyr / letters
    }

    /** Условие “ответ на русском”: есть кириллица и доля >= порога */
    fun isRussianEnough(
        s: String,
        minRatio: Double = 0.6,
    ): Boolean = hasCyrillic(s) && cyrillicRatio(s) >= minRatio
}
