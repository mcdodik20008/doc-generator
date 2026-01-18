package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.model.KDocParsed
import com.bftcom.docgenerator.graph.api.node.KDocFetcher
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtDeclaration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class KDocFetcherImpl : KDocFetcher {
    private val log = LoggerFactory.getLogger(KDocFetcher::class.java)

    override fun parseKDoc(decl: KtDeclaration): KDocParsed? {
        val kdoc = decl.docComment ?: findKDocAbove(decl) ?: return null

        val defaultSection = try {
            kdoc.getDefaultSection()
        } catch (e: Exception) { null }

        val raw = stripCommentMarkers(kdoc.text).trim()
        val defContent = defaultSection?.getContent()?.trim().orEmpty()
        val (summary, description) = splitSummary(defContent.ifBlank { raw })

        val params = mutableMapOf<String, String>()
        val properties = mutableMapOf<String, String>()
        val throws = mutableMapOf<String, String>()
        var returns: String? = null
        var since: String? = null
        val seeAlso = mutableListOf<String>()
        val other = mutableMapOf<String, MutableList<String>>()

        kdoc.getAllSections().forEach { section ->
            parseSectionTags(section) { name, subject, content ->
                when (name) {
                    "param" -> if (subject != null) params[subject] = content
                    "property" -> if (subject != null) properties[subject] = content
                    "return", "returns" -> returns = content
                    "throws", "exception" -> if (subject != null) throws[subject] = content
                    "see" -> seeAlso += content.ifBlank { subject ?: "" }
                    "since" -> since = content.ifBlank { subject ?: "" }
                    else -> other.getOrPut(name) { mutableListOf() } +=
                        if (content.isNotBlank()) content else (subject ?: "")
                }
            }
        }

        val result = KDocParsed(
            raw = raw,
            summary = summary.ifBlank { null },
            description = description.ifBlank { null },
            params = params,
            properties = properties,
            returns = returns?.ifBlank { null },
            throws = throws,
            seeAlso = seeAlso.filter { it.isNotBlank() },
            since = since?.ifBlank { null },
            otherTags = other.mapValues { it.value.filter { s -> s.isNotBlank() } }
        )

        // Проверка на пустоту (чтобы тест на пустой коммент вернул null)
        return if (isKDocEmpty(result)) null else result
    }

    override fun toDocString(k: KDocParsed): String {
        val out = StringBuilder()
        fun ln(s: String = "") { out.appendLine(s) }

        k.summary?.let { ln(it) }
        if (!k.description.isNullOrBlank()) {
            if (out.isNotEmpty()) ln()
            ln(k.description!!)
        }

        appendMap(out, "Properties:", k.properties)
        appendMap(out, "Parameters:", k.params)

        k.returns?.let {
            if (out.isNotEmpty()) ln()
            ln("Returns: $it")
        }

        appendMap(out, "Throws:", k.throws)

        if (k.seeAlso.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("See also:")
            k.seeAlso.forEach { ln("  - $it") }
        }

        k.since?.let {
            if (out.isNotEmpty()) ln()
            ln("Since: $it")
        }

        if (k.otherTags.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("Tags:")
            k.otherTags.forEach { (tag, vals) ->
                vals.forEach { ln("  - @$tag $it") }
            }
        }

        return out.toString().trim()
    }

    override fun toMeta(k: KDocParsed?): Map<String, Any?>? = k?.let {
        mapOf(
            "summary" to it.summary,
            "description" to it.description,
            "params" to it.params,
            "properties" to it.properties,
            "returns" to it.returns,
            "throws" to it.throws,
            "seeAlso" to it.seeAlso,
            "since" to it.since,
            "otherTags" to it.otherTags,
        )
    }

    // ---------------- Helpers ----------------

    private fun parseSectionTags(
        section: KDocSection,
        onTag: (name: String, subject: String?, content: String) -> Unit
    ) {
        section.children.forEach { child ->
            val node = child.node
            val tagNameNode = node.findChildByType(KDocTokens.TAG_NAME)
            if (tagNameNode != null) {
                val name = tagNameNode.text.removePrefix("@").lowercase()

                val subject = try {
                    val m = child.javaClass.getMethod("getSubjectName")
                    m.invoke(child) as? String
                } catch (e: Exception) {
                    child.children.firstOrNull { it.node.elementType.toString().contains("VALUE") }?.text
                }

                val content = try {
                    val m = child.javaClass.getMethod("getContent")
                    m.invoke(child) as? String ?: ""
                } catch (e: Exception) {
                    child.text.substringAfter(subject ?: name).trim()
                }

                onTag(name, subject, content.trim())
            }
        }
    }

    private fun isKDocEmpty(k: KDocParsed): Boolean {
        return k.summary.isNullOrBlank() &&
                k.description.isNullOrBlank() &&
                k.params.isEmpty() &&
                k.properties.isEmpty() &&
                k.returns.isNullOrBlank() &&
                k.throws.isEmpty() &&
                k.seeAlso.isEmpty() &&
                k.since.isNullOrBlank() &&
                k.otherTags.isEmpty()
    }

    private fun findKDocAbove(decl: KtDeclaration): KDoc? {
        var cur: PsiElement? = decl.prevSibling
        while (cur != null) {
            if (cur is KDoc) return cur
            if (cur !is PsiWhiteSpace && cur !is PsiComment) break
            cur = cur.prevSibling
        }
        return null
    }

    private fun splitSummary(text: String): Pair<String, String> {
        val t = text.replace("\r\n", "\n")
        val parts = t.split(Regex("\n\\s*\n"), limit = 2)
        return (parts.getOrNull(0)?.trim().orEmpty()) to (parts.getOrNull(1)?.trim().orEmpty())
    }

    private fun stripCommentMarkers(text: String): String {
        return text.removePrefix("/**").removeSuffix("*/")
            .lineSequence()
            .map { it.trimStart().removePrefix("*").trimStart() }
            .joinToString("\n")
    }

    private fun appendMap(sb: StringBuilder, header: String, data: Map<String, String>) {
        if (data.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.appendLine()
            sb.appendLine(header)
            data.forEach { (n, v) -> sb.appendLine("  - $n — $v") }
        }
    }
}
