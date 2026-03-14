package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.bftcom.docgenerator.ai.resilience.ResilientExecutor
import org.springframework.stereotype.Component

/**
 * Генерация `doc_digest` (строгий kv-lines) из `doc_tech`.
 * Использует DirectLlmClient (прямой HTTP) вместо Spring AI ChatClient.
 */
@Component
class NodeDocDigestClient(
    private val directLlm: DirectLlmClient,
    private val props: AiClientsProperties,
    private val resilientExecutor: ResilientExecutor? = null,
) {
    private val systemPrompt =
        """
        Ты — генератор строгих дайджестов документации.
        Твоя задача: по входному `doc_tech` сформировать `doc_digest` в строгом формате kv-lines.

        КРИТИЧЕСКИ ВАЖНО:
        - Пиши ТОЛЬКО на русском.
        - Никаких Markdown-заголовков, списков кроме строк формата key=value или "- key: value".
        - Никаких пояснений, вступлений, заключений. Только дайджест.
        - Не выдумывай факты. Если поле неизвестно — пиши value="unknown".
        - Строки должны быть короткими (по возможности).

        ОБЯЗАТЕЛЬНЫЕ КЛЮЧИ:
        kind=<NODE_KIND>
        fqn=<FQN>
        purpose=<кратко>
        inputs=<кратко/unknown>
        outputs=<кратко/unknown>
        side_effects=<кратко/none/unknown>
        deps=<через запятую или none>
        notes=<важные ограничения или none>
        """.trimIndent()

    fun generate(kind: String, fqn: String, docTech: String, deps: List<String>): String {
        val depsLine = if (deps.isEmpty()) "none" else deps.joinToString(",").take(800)
        val user =
            buildString {
                appendLine("NODE_KIND=$kind")
                appendLine("FQN=$fqn")
                appendLine("DEPS=$depsLine")
                appendLine()
                appendLine("DOC_TECH:")
                appendLine(docTech.trim())
            }

        val operation = {
            directLlm.call(
                DirectLlmClient.LlmRequest(
                    model = props.coder.model,
                    systemPrompt = systemPrompt,
                    userPrompt = user,
                    temperature = props.coder.temperature,
                    topP = props.coder.topP,
                    seed = props.coder.seed,
                ),
            )
        }

        return if (resilientExecutor != null) {
            resilientExecutor.executeString("generate-digest:$fqn", operation)
        } else {
            operation()
        }
    }
}
