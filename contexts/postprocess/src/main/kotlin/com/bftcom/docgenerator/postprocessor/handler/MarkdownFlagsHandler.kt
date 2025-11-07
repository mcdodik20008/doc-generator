package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.springframework.stereotype.Component

@Component
class MarkdownFlagsHandler : PostprocessHandler {
    private val mdPats =
        listOf(
            Regex("""(?m)^\s{0,3}#{1,6}\s"""),
            Regex("""\[[^\]]+]\([^)]+\)"""),
            Regex("""`{1,3}[^`]+`{1,3}"""),
            Regex("""(?m)^\s{0,3}[-*+]\s+"""),
            Regex("""\*\*[^*]+\*\*|__[^_]+__"""),
        )

    private fun isMd(text: String) = mdPats.any { it.containsMatchIn(text) }

    override fun supports(s: ChunkSnapshot) = true

    override fun produce(s: ChunkSnapshot): PartialMutation {
        val flag = if (isMd(s.content)) "md" else null
        return PartialMutation()
            .set(FieldKey.USES_MD, flag)
            .set(FieldKey.USED_BY_MD, flag)
    }
}
