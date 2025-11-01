package com.bftcom.docgenerator.chunking.model

import com.bftcom.docgenerator.ai.model.CoderExplainRequest
import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.node.Node

fun Chunk.toCoderExplainRequest(): CoderExplainRequest {
    val node = this.node
    val lang = langDetected ?: node.lang.name.lowercase()

    // если уже вырезанный метод — используем его напрямую
    val codeExcerpt = node.sourceCode!!.trim()

    val hints = buildRichHints(this, node).ifBlank { null }

    return CoderExplainRequest(
        nodeFqn = this.title?.takeIf { it.isNotBlank() } ?: node.fqn,
        language = lang,
        hints = hints,
        codeExcerpt = codeExcerpt,
        lineStart = node.lineStart,
        lineEnd = node.lineEnd,
    )
}

fun Chunk.toTalkerRewriteRequest(): TalkerRewriteRequest {
    val lang = this.langDetected ?: this.node.lang.name
    return TalkerRewriteRequest(
        nodeFqn = this.title ?: this.node.fqn,
        language = lang,
        rawContent = this.contentRaw ?: throw RuntimeException("Нет содержимого для объяснения"),
    )
}

/**
 * Очень насыщенные подсказки для кодера: максимум структурированной меты, но кратко.
 */
private fun buildRichHints(
    chunk: Chunk,
    node: Node,
): String =
    buildString {
        val meta = node.meta as? Map<*, *>

        fun put(
            label: String,
            value: String?,
        ) {
            if (!value.isNullOrBlank()) appendLine("$label: $value")
        }

        fun putList(
            label: String,
            values: List<String>?,
            limit: Int = 12,
        ) {
            val vs = values?.filter { it.isNotBlank() }?.distinct().orEmpty()
            if (vs.isNotEmpty()) appendLine("$label: ${vs.take(limit).joinToString(", ")}")
            if (vs.size > limit) appendLine("$label(+): ${vs.size - limit} more …")
        }

        // 1) Кто мы и где мы
        put("Kind", node.kind.name)
        put("Package", meta?.get("pkgFqn") as? String ?: node.packageName)
        put("Owner", meta?.get("ownerFqn") as? String)
        put("File", node.filePath)
        val lineStart = node.lineStart
        val lineEnd = node.lineEnd
        if (lineStart != null && lineEnd != null) put("Span", "$lineStart..$lineEnd")

        // 2) Сигнатуры и типы
        put("Signature", (meta?.get("signature") ?: node.signature) as? String)
        @Suppress("UNCHECKED_CAST")
        putList("Params", (meta?.get("params") as? List<String>)?.map { it.ifBlank { "_" } })
        @Suppress("UNCHECKED_CAST")
        put("ReturnType", meta?.get("returnType") as? String)
        @Suppress("UNCHECKED_CAST")
        putList("Supertypes", (meta?.get("supertypesSimple") as? List<String>))

        // 3) Аннотации/модификаторы → подсказать роль
        @Suppress("UNCHECKED_CAST")
        val annotations = (meta?.get("annotations") as? List<String>).orEmpty()
        putList("Annotations", annotations)
        @Suppress("UNCHECKED_CAST")
        val modifiers =
            (meta?.get("modifiers") as? Map<String, Any>)
                ?.entries
                ?.filter { (_, v) -> v is Boolean && v || v is String }
                ?.joinToString { "${it.key}=${it.value}" }
        put("Modifiers", modifiers)

        // 4) KDoc
        val kdoc = (meta?.get("kdoc") as? Map<*, *>)
        put("KDoc-Summary", kdoc?.get("summary") as? String)
        put("KDoc-Details", (kdoc?.get("details") as? String)?.take(300))

        // 5) Импорты (частично)
        @Suppress("UNCHECKED_CAST")
        putList("Imports", (meta?.get("imports") as? List<String>), limit = 8)

        // 6) Контекст пайплайна
        val pipeline = chunk.pipeline as? Map<*, *>
        val params = pipeline?.get("params") as? Map<*, *>
        put("Chunking-Strategy", params?.get("strategy")?.toString())
        put("Audience", params?.get("audience")?.toString()) // например: "coder" / "docs"
        put("Goal", params?.get("goal")?.toString())
        put("QualityTarget", params?.get("quality")?.toString())

        // 7) Быстрые эвристики по роли
        val isTest = (node.filePath ?: "").contains("test", ignoreCase = true)
        if (isTest) appendLine("Hint: Probably a test context.")

        val looksEndpoint = annotations.any { it.endsWith("Mapping") }
        val looksJob = annotations.any { it.contains("Scheduled") }
        val looksListener = annotations.any { it.contains("KafkaListener") }
        if (looksEndpoint) appendLine("Role: HTTP endpoint.")
        if (looksJob) appendLine("Role: Scheduled job.")
        if (looksListener) appendLine("Role: Kafka listener.")

        // 8) Граф-подсказки по вызовам
        @Suppress("UNCHECKED_CAST")
        val rawUsages = (meta?.get("rawUsages") as? List<Map<String, Any?>>).orEmpty()
        if (rawUsages.isNotEmpty()) {
            val (intra, inter) = splitUsages(rawUsages, meta?.get("ownerFqn") as? String)
            appendLine("Calls-IntraClass: ${intra.size}")
            appendLine("Calls-External: ${inter.size}")
            val sampleIntra = intra.take(5).joinToString { it }
            val sampleInter = inter.take(5).joinToString { it }
            if (sampleIntra.isNotBlank()) appendLine("Intra-Sample: $sampleIntra")
            if (sampleInter.isNotBlank()) appendLine("External-Sample: $sampleInter")
        }

        // 9) Итоговая инструкция для LLM — просим структуру
        appendLine()
        appendLine("=== Instructions ===")
        appendLine("Explain the code precisely for engineers. Include:")
        appendLine("1) Purpose and high-level behavior;")
        appendLine("2) Parameters, return type, invariants, pre/post-conditions;")
        appendLine("3) Algorithm steps and complexity;")
        appendLine("4) Side effects, exceptions, concurrency/thread-safety notes;")
        appendLine("5) Important dependencies (by name) and how they are used;")
        appendLine("6) Edge cases and failure modes;")
        appendLine("7) A short usage example (Kotlin);")
        appendLine("8) If endpoint/listener/job — describe input/output and idempotency;")
        appendLine("9) If test — summarize scenario and what is asserted.")
    }.trim()

private fun splitUsages(
    ruList: List<Map<String, Any?>>,
    ownerFqn: String?,
): Pair<List<String>, List<String>> {
    val intra = mutableListOf<String>()
    val inter = mutableListOf<String>()

    ruList.forEach { m ->
        // поддержка двух форм: Simple(name) и Dot(receiver, member)
        val name = (m["name"] as? String)
        val recv = (m["receiver"] as? String)
        val member = (m["member"] as? String)
        val callee =
            when {
                !member.isNullOrBlank() && !recv.isNullOrBlank() -> "$recv.$member"
                !name.isNullOrBlank() -> name
                else -> null
            } ?: return@forEach

        if (!recv.isNullOrBlank() && ownerFqn != null && recv == ownerFqn) {
            intra += callee
        } else {
            inter += callee
        }
    }
    return intra to inter
}
