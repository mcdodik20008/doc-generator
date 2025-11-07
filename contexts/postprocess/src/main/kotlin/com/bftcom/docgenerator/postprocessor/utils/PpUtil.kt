package com.bftcom.docgenerator.postprocessor.utils

import java.security.MessageDigest

object PpUtil {
    fun sha256Hex(s: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private val tokenRx = Regex("""\S+""")

    fun tokenCount(s: String) = tokenRx.findAll(s).count()

    fun spanCharsRange(s: String) = "[0,${s.length})"

    private val mdPats =
        listOf(
            Regex("""(?m)^\s{0,3}#{1,6}\s"""),
            Regex("""\[[^\]]+]\([^)]+\)"""),
            Regex("""`{1,3}[^`]+`{1,3}"""),
            Regex("""(?m)^\s{0,3}[-*+]\s+"""),
            Regex("""\*\*[^*]+\*\*|__[^_]+__"""),
        )

    fun usesMarkdown(s: String) = mdPats.any { it.containsMatchIn(s) }

    fun explainQualityJson(
        tokens: Int,
        len: Int,
    ): String {
        val grade =
            when {
                tokens >= 300 || len >= 2000 -> "A"
                tokens >= 120 || len >= 800 -> "B"
                else -> "C"
            }
        return """{"grade":"$grade","tokens":$tokens,"length":$len}"""
    }

    fun explainMd(
        text: String,
        tokens: Int,
    ): String {
        val head = text.replace("\n", " ").take(240) + if (text.length > 240) "…" else ""
        return "### Summary\n- length: ${text.length}\n- tokens: $tokens\n\n#### Preview\n$head\n"
    }
}
