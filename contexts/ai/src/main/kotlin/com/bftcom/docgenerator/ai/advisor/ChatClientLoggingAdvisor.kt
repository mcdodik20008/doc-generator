package com.bftcom.docgenerator.ai.advisor

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.ai.chat.messages.MessageType
import org.springframework.stereotype.Component

/**
 * Advisor для качественного логирования обращений к LLM.
 * Логирует модель, размеры промптов (system/user), оценку токенов, время и реальное использование токенов.
 *
 * Полный текст запроса/ответа выводится, если для клиента включён debug
 * (spring.ai.clients.coder.debug=true / spring.ai.clients.talker.debug=true).
 * Иначе — сокращённый preview (начало + конец).
 */
@Component
class ChatClientLoggingAdvisor(
    props: AiClientsProperties,
) : BaseAdvisor {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Множество моделей, для которых включён debug-лог. */
    private val debugModels: Set<String> =
        buildSet {
            if (props.coder.debug) add(props.coder.model)
            if (props.talker.debug) add(props.talker.model)
        }

    companion object {
        private val requestStartTime = ThreadLocal<Long>()
        private const val HEAD_LEN = 120
        private const val TAIL_LEN = 120
        private const val AVG_CHARS_PER_TOKEN = 3.5
    }

    override fun getOrder(): Int = 0

    override fun before(
        chatClientRequest: ChatClientRequest,
        advisorChain: AdvisorChain,
    ): ChatClientRequest {
        val now = System.currentTimeMillis()
        requestStartTime.set(now)

        val prompt = chatClientRequest.prompt()

        val model =
            try {
                prompt.options.model
            } catch (_: Exception) {
                null
            } ?: "<unknown>"

        val systemChars: Int
        val userChars: Int
        val userText: String
        try {
            val messages = prompt.instructions
            systemChars =
                messages
                    .filter { it.messageType == MessageType.SYSTEM }
                    .sumOf { it.text?.length ?: 0 }
            val userMessages = messages.filter { it.messageType == MessageType.USER }
            userChars = userMessages.sumOf { it.text?.length ?: 0 }
            userText = userMessages.joinToString("\n") { it.text ?: "" }
        } catch (ex: Exception) {
            log.warn("Failed to extract prompt messages for LLM logging: {}", ex.message)
            return chatClientRequest
        }

        val totalChars = systemChars + userChars
        val estimatedTokens = (totalChars / AVG_CHARS_PER_TOKEN).toInt()

        val sb = StringBuilder()
        sb.append("LLM request started:")
        sb.append("\n  model=$model, chars=$totalChars (system=$systemChars, user=$userChars), estimatedTokens=~$estimatedTokens")

        if (isDebugFor(model)) {
            sb.append("\n  full prompt:\n").append(userText)
        } else {
            sb.append("\n  preview: ").append(shortenForLog(userText))
        }

        log.info("{}", sb.toString())

        return chatClientRequest
    }

    override fun after(
        chatClientResponse: ChatClientResponse,
        advisorChain: AdvisorChain,
    ): ChatClientResponse {
        val now = System.currentTimeMillis()
        val startedAt = requestStartTime.get()
        if (startedAt != null) {
            requestStartTime.remove()
        }
        val durationMs = startedAt?.let { now - it }

        val chatResponse = chatClientResponse.chatResponse()
        val metadata =
            try {
                chatResponse?.metadata
            } catch (ex: Exception) {
                log.warn("Failed to extract ChatResponse metadata for LLM logging: {}", ex.message)
                null
            }

        val model =
            try {
                metadata?.model
            } catch (_: Exception) {
                null
            } ?: "<unknown>"

        val responseText =
            try {
                chatResponse?.result?.output?.text ?: ""
            } catch (ex: Exception) {
                log.warn("Failed to extract response content for LLM logging: {}", ex.message)
                ""
            }

        val usage =
            try {
                metadata?.usage
            } catch (_: Exception) {
                null
            }

        val promptTokens = usage?.promptTokens
        val completionTokens = usage?.completionTokens
        val totalTokens = usage?.totalTokens

        val sb = StringBuilder()
        sb.append("LLM response received:")
        sb.append("\n  model=$model")
        if (durationMs != null) sb.append(", durationMs=$durationMs")
        sb.append(", tokens[prompt=$promptTokens, completion=$completionTokens, total=$totalTokens]")
        sb.append(", chars=${responseText.length}")

        if (isDebugFor(model)) {
            sb.append("\n  full response:\n").append(responseText)
        } else {
            sb.append("\n  preview: ").append(shortenForLog(responseText))
        }

        log.info("{}", sb.toString())

        return chatClientResponse
    }

    private fun isDebugFor(model: String): Boolean = model in debugModels

    /**
     * Обрезает текст для логов: первые HEAD_LEN и последние TAIL_LEN символов.
     */
    internal fun shortenForLog(text: String): String {
        if (text.isBlank()) return "<empty>"
        val clean = text.replace('\n', ' ').replace('\r', ' ')
        if (clean.length <= HEAD_LEN + TAIL_LEN) return clean
        return clean.substring(0, HEAD_LEN) + " ... " + clean.substring(clean.length - TAIL_LEN)
    }
}
