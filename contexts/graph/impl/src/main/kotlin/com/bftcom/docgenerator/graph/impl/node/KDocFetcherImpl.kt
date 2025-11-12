package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.node.KDocFetcher
import com.bftcom.docgenerator.graph.api.model.KDocParsed
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
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
        // 1) Пытаемся получить стандартным способом
        val kdoc =
            decl.docComment ?: findKDocAbove(decl) ?: run {
                log.warn("KDoc not found for declaration: ${decl.name}")
                return null
            }

        // 2) Берём default section, если есть (бывает, что её нет — тогда работаем по raw-тексту)
        val def: KDocSection? =
            try {
                kdoc.getDefaultSection()
            } catch (_: Throwable) {
                null
            }

        val raw = stripCommentMarkers(kdoc.text).trim()
        val defContent = def?.getContent()?.trim().orEmpty()
        val (summary, description) = splitSummary(defContent.ifBlank { raw })

        // 3) Парсим теги без типов KDocTag: обходим детей и ищем токены KDOC_TAG, KDOC_TAG_NAME и т.п.
        val params = mutableMapOf<String, String>()
        val properties = mutableMapOf<String, String>()
        val throws = mutableMapOf<String, String>()
        var returns: String? = null
        var since: String? = null
        val seeAlso = mutableListOf<String>()
        val other = mutableMapOf<String, MutableList<String>>()

        parseTags(def ?: kdoc, onTag = { name, subject, content ->
            when (name) {
                "param" -> if (subject != null) params[subject] = content
                "property" -> if (subject != null) properties[subject] = content
                "return", "returns" -> returns = content
                "throws", "exception" -> if (subject != null) throws[subject] = content
                "see" -> seeAlso += content.ifBlank { subject ?: "" }
                "since" -> since = content.ifBlank { subject ?: "" }
                else -> {
                    val bucket = other.getOrPut(name) { mutableListOf() }
                    bucket += if (content.isNotBlank()) content else (subject ?: "")
                }
            }
        })

        val hasAny =
            raw.isNotBlank() ||
                summary.isNotBlank() ||
                description.isNotBlank() ||
                params.isNotEmpty() || properties.isNotEmpty() ||
                !returns.isNullOrBlank() ||
                throws.isNotEmpty() || seeAlso.isNotEmpty() ||
                !since.isNullOrBlank() || other.isNotEmpty()

        if (!hasAny) {
            log.warn("KDoc is empty for declaration: ${decl.name}")
            return null
        }

        return KDocParsed(
            raw = raw,
            summary = summary.ifBlank { null },
            description = description.ifBlank { null },
            params = params,
            properties = properties,
            returns = returns?.ifBlank { null },
            throws = throws,
            seeAlso = seeAlso.filter { it.isNotBlank() },
            since = since?.ifBlank { null },
            otherTags = other.mapValues { it.value.filter { s -> s.isNotBlank() } },
        )
    }

    /** Формат для Node.doc_comment */
    override fun toDocString(k: KDocParsed): String {
        val out = StringBuilder()

        fun ln(s: String = "") {
            out.appendLine(s)
        }

        k.summary?.let { ln(it) }
        if (!k.description.isNullOrBlank()) {
            if (out.isNotEmpty()) ln()
            ln(k.description.toString())
        }

        if (k.properties.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("Properties:")
            k.properties.forEach { (n, v) -> ln("  - $n — $v") }
        }

        if (k.params.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("Parameters:")
            k.params.forEach { (n, v) -> ln("  - $n — $v") }
        }

        if (!k.returns.isNullOrBlank()) {
            if (out.isNotEmpty()) ln()
            ln("Returns: ${k.returns}")
        }

        if (k.throws.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("Throws:")
            k.throws.forEach { (t, v) -> ln("  - $t — $v") }
        }

        if (k.seeAlso.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("See also:")
            k.seeAlso.forEach { v -> ln("  - $v") }
        }

        if (!k.since.isNullOrBlank()) {
            if (out.isNotEmpty()) ln()
            ln("Since: ${k.since}")
        }

        if (k.otherTags.isNotEmpty()) {
            if (out.isNotEmpty()) ln()
            ln("Tags:")
            k.otherTags.forEach { (tag, vals) ->
                vals.forEach { v -> ln("  - @$tag $v") }
            }
        }

        return out.toString().trim()
    }

    /** Формат для meta.kdoc */
    override fun toMeta(k: KDocParsed?): Map<String, Any?>? =
        k?.let {
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

    // ---------------- helpers ----------------

    /**
     * Иногда PSI не заполняет decl.docComment. Тогда ищем KDoc над декларацией вручную.
     */
    private fun findKDocAbove(decl: KtDeclaration): KDoc? {
        // 1) Пробуем среди прямых сиблингов «выше»
        var cur: PsiElement? = decl.prevSibling
        while (cur != null && (cur is PsiWhiteSpace || cur is PsiComment || cur.text.isBlank())) {
            if (cur is KDoc) return cur
            cur = cur.prevSibling
        }
        // 2) Если не нашли — берём у родителя и ищем непосредственного KDoc перед декларацией
        val parent = decl.parent ?: return null
        val children = parent.children
        val idx = children.indexOf(decl)
        if (idx > 0) {
            var j = idx - 1
            while (j >= 0) {
                val e = children[j]
                if (e is KDoc) return e
                if (e !is PsiWhiteSpace && e !is PsiComment && e !is KDoc) break
                j--
            }
        }
        return null
    }

    /**
     * Универсальный парсер тегов без зависимостей на KDocTag/KDocSection классы.
     * Пробегаем все дочерние элементы и собираем блоки, начинающиеся с KDOC_TAG.
     */
    private fun parseTags(
        root: PsiElement,
        onTag: (name: String, subject: String?, content: String) -> Unit,
    ) {
        // Идём глубоко по дереву и выхватываем элементы-теги
        val all = PsiTreeUtil.collectElements(root) { true }
        // Группируем по "логическим" тегам: KDOC_TAG (содержит внутри KDOC_TAG_NAME, KDOC_TEXT и т.д.)
        all
            .filter { it.node?.elementType == KDocTokens.TAG_NAME }
            .forEach { tagEl ->
                // Имя тега
                val nameEl = tagEl.node.findChildByType(KDocTokens.TAG_NAME)
                val name = nameEl?.text?.removePrefix("@")?.lowercase() ?: return@forEach

                // Субъект тега (имя параметра/свойства/исключения) — первый KDOC_TAG_VALUE
                val valueEl = tagEl.node.findChildByType(KDocTokens.TAG_NAME)
                val subject = valueEl?.text?.trim()?.takeIf { it.isNotEmpty() }

                // Текст тега — все KDOC_TEXT потомки, склеенные
                val textParts = mutableListOf<String>()
                var child = tagEl.firstChild
                while (child != null) {
                    if (child.node?.elementType == KDocTokens.TEXT) {
                        textParts += child.text
                    }
                    child = child.nextSibling
                }
                val content = textParts.joinToString("").trim()

                onTag(name, subject, content)
            }
    }

    private fun splitSummary(text: String): Pair<String, String> {
        val t = text.replace("\r\n", "\n")
        val parts = t.split(Regex("\n\\s*\n"), limit = 2)
        val summary = parts.getOrNull(0)?.trim().orEmpty()
        val description = parts.getOrNull(1)?.trim().orEmpty()
        return summary to description
    }

    private fun stripCommentMarkers(text: String): String {
        val noStart = text.removePrefix("/**").removeSuffix("*/")
        return noStart
            .lineSequence()
            .map { it.trimStart().removePrefix("*").trimStart() }
            .joinToString("\n")
    }
}
