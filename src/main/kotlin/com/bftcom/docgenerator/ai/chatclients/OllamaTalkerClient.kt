package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.model.TalkerRewriteRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class OllamaTalkerClient(
    @param:Qualifier("talkerChatClient")
    private val chat: ChatClient
) {
    fun rewrite(req: TalkerRewriteRequest): String {
        val user = buildString {
            appendLine("Перепиши объяснение простым языком (3–6 предложений) без домыслов.")
            appendLine("Node: ${req.nodeFqn}, Language: ${req.language}")
            appendLine("Техническое объяснение (источник):")
            appendLine(req.rawContent.trim())
        }

        val result = chat.prompt().user(user).call().content()
        return result.orEmpty().trim()
    }
}