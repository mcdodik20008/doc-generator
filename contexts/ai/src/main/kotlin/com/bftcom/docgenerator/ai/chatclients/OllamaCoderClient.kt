package com.bftcom.docgenerator.ai.chatclients

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import com.bftcom.docgenerator.ai.resilience.ResilientExecutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Клиент для генерации технических объяснений кода через LLM (coder этап).
 * Использует DirectLlmClient (прямой HTTP) вместо Spring AI ChatClient.
 */
@Component
class OllamaCoderClient(
    private val directLlm: DirectLlmClient,
    private val props: AiClientsProperties,
    private val resilientExecutor: ResilientExecutor? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(
        context: String,
        systemPrompt: String,
    ): String {
        require(context.isNotBlank()) { "Context cannot be blank" }
        require(systemPrompt.isNotBlank()) { "System prompt cannot be blank" }

        val operation = {
            directLlm.call(
                DirectLlmClient.LlmRequest(
                    model = props.coder.model,
                    systemPrompt = systemPrompt,
                    userPrompt = context,
                    temperature = props.coder.temperature,
                    topP = props.coder.topP,
                    seed = props.coder.seed,
                ),
            )
        }

        return if (resilientExecutor != null) {
            resilientExecutor.executeString("coder-generate", operation)
        } else {
            operation()
        }
    }
}
