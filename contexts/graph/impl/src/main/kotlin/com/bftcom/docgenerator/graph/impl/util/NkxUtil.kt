package com.bftcom.docgenerator.graph.impl.util

internal object NkxUtil {
    fun anns(raw: List<String>) = raw.map { it.substringAfterLast('.').lowercase() }.toSet()

    fun supers(raw: List<String>) = raw.map { it.substringAfterLast('.').lowercase() }.toSet()

    fun imps(raw: List<String>?) = raw.orEmpty().map { it.lowercase() }

    fun name(raw: String?) = raw.orEmpty()

    fun pkg(raw: String?) = raw.orEmpty().lowercase()

    fun hasAnyAnn(
        a: Set<String>,
        vararg keys: String,
    ) = keys.any { it.lowercase() in a }

    fun superContains(
        s: Set<String>,
        vararg keys: String,
    ) = keys.any { it.lowercase() in s }

    fun importsContain(
        imps: List<String>,
        vararg parts: String,
    ) = parts.any { p -> imps.any { it.contains(p.lowercase()) } }

    fun nameEnds(
        n: String,
        vararg suff: String,
    ) = suff.any { n.endsWith(it, ignoreCase = true) }

    fun nameContains(
        n: String,
        vararg subs: String,
    ) = subs.any { n.contains(it, ignoreCase = true) }
}
