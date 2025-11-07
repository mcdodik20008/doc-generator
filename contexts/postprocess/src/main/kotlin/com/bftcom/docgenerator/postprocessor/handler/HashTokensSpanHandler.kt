package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class HashTokensSpanHandler : PostprocessHandler {
    override fun supports(s: ChunkSnapshot) = true

    override fun produce(s: ChunkSnapshot): PartialMutation {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(s.content.toByteArray())
                .joinToString("") { "%02x".format(it) }
        val tokens = Regex("""\S+""").findAll(s.content).count()
        val span = "[0,${s.content.length})"
        return PartialMutation()
            .set(FieldKey.CONTENT_HASH, hash)
            .set(FieldKey.TOKEN_COUNT, tokens)
            .set(FieldKey.SPAN_CHARS, span)
    }
}
