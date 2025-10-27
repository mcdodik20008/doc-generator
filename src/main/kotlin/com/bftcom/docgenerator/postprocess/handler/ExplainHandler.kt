package com.bftcom.docgenerator.postprocess.handler

import com.bftcom.docgenerator.postprocess.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocess.model.FieldKey
import com.bftcom.docgenerator.postprocess.model.PartialMutation
import org.springframework.stereotype.Component

@Component
class ExplainHandler : PostprocessHandler {
    override fun supports(s: ChunkSnapshot) = true
    override fun produce(s: ChunkSnapshot): PartialMutation {
        val tokens = Regex("""\S+""").findAll(s.content).count()
        val head = s.content.replace("\n", " ").take(240) + if (s.content.length > 240) "…" else ""
        val md = "### Summary\n- length: ${s.content.length}\n- tokens: $tokens\n\n#### Preview\n$head\n"
        val grade = when {
            tokens >= 300 || s.content.length >= 2000 -> "A"
            tokens >= 120 || s.content.length >= 800  -> "B"
            else -> "C"
        }
        val quality = """{"grade":"$grade","tokens":$tokens,"length":${s.content.length}}"""
        return PartialMutation()
            .set(FieldKey.EXPLAIN_MD, md)
            .set(FieldKey.EXPLAIN_QUALITY_JSON, quality)
    }
}
