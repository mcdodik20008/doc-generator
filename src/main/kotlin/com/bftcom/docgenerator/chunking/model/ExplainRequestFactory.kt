package com.bftcom.docgenerator.chunking.model

import com.bftcom.docgenerator.ai.model.CoderExplainRequest
import com.bftcom.docgenerator.domain.chunk.Chunk


fun Chunk.toCoderExplainRequest(): CoderExplainRequest {
    val lang = this.langDetected ?: this.node.lang.name
    val code = this.node.sourceCode ?: "none"
    val pipeline = this.pipeline

    val hints = buildString {
        val p = (pipeline as? Map<*, *>)?.get("params") as? Map<*, *>
        val sig = p?.get("signature")?.toString()
        if (!sig.isNullOrBlank()) appendLine("Signature: $sig")
    }.ifBlank { null }

    return CoderExplainRequest(
        nodeFqn = this.title ?: this.node.fqn,
        language = lang,
        hints = hints,
        codeExcerpt = code
    )
}

