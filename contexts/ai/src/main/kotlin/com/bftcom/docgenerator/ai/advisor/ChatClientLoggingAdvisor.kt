package com.bftcom.docgenerator.ai.advisor

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.stereotype.Component

/**
 * Advisor для качественного логирования обращений к LLM.
 * Логирует сокращенный контент (начало/конец), название модели, время и токены.
 */
@Component
class ChatClientLoggingAdvisor : BaseAdvisor {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val requestStartTime = ThreadLocal<Long>()
        private const val HEAD_LEN = 100
        private const val TAIL_LEN = 200

        @Volatile
        private var structureLogged = false
    }

    override fun getOrder(): Int = 0

    override fun before(
        chatClientRequest: ChatClientRequest,
        advisorChain: AdvisorChain
    ): ChatClientRequest {
        logStructureOnce()

        val now = System.currentTimeMillis()
        requestStartTime.set(now)

        val prompt = chatClientRequest.prompt()
        val promptText = try {
            // Prompt.getContents()
            prompt?.contents ?: ""
        } catch (ex: Exception) {
            log.warn("Failed to extract prompt contents for LLM logging: {}", ex.message)
            ""
        }

        val modelFromOptions = try {
            // Prompt.getOptions().getModel()
            prompt.options.model
        } catch (ex: Exception) {
            null
        }

        val model = modelFromOptions ?: "<unknown>"

        val preview = shortenForLog(promptText)

        log.info(
            "LLM request started: model={}, chars={}, preview={}",
            model,
            promptText.length,
            preview
        )
        // Явно фиксируем, что ожидание ответа может быть долгим
        log.debug("LLM request in progress, response may take noticeable time...")

        // Никак не модифицируем запрос
        return chatClientRequest
    }

    override fun after(
        chatClientResponse: ChatClientResponse,
        advisorChain: AdvisorChain
    ): ChatClientResponse {
        val now = System.currentTimeMillis()
        val startedAt = requestStartTime.get()
        if (startedAt != null) {
            requestStartTime.remove()
        }
        val durationMs = startedAt?.let { now - it }

        val chatResponse = chatClientResponse.chatResponse()
        val metadata = try {
            chatResponse?.metadata
        } catch (ex: Exception) {
            log.warn("Failed to extract ChatResponse metadata for LLM logging: {}", ex.message)
            null
        }

        val modelFromMetadata = try {
            metadata?.model
        } catch (ex: Exception) {
            null
        }
        val model = modelFromMetadata ?: "<unknown>"

        val responseText = try {
            // chatResponse.getResult().getOutput().getContent()
            chatResponse
                ?.result
                ?.output
                ?.text
                ?: ""
        } catch (ex: Exception) {
            log.warn("Failed to extract response content for LLM logging: {}", ex.message)
            ""
        }

        val preview = shortenForLog(responseText)

        val usage = try {
            metadata?.usage
        } catch (ex: Exception) {
            null
        }

        val promptTokens = usage?.promptTokens
        val completionTokens = usage?.completionTokens
        val totalTokens = usage?.totalTokens

        if (durationMs != null) {
            log.info(
                "LLM response received: model={}, durationMs={}, tokens[prompt={}, completion={}, total={}], chars={}, preview={}",
                model,
                durationMs,
                promptTokens,
                completionTokens,
                totalTokens,
                responseText.length,
                preview
            )
        } else {
            log.info(
                "LLM response received: model={}, tokens[prompt={}, completion={}, total={}], chars={}, preview={}",
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                responseText.length,
                preview
            )
        }

        // Никак не модифицируем ответ
        return chatClientResponse
    }

    /**
     * Логируем один раз формат логов, чтобы потом по ним было проще ориентироваться.
     */
    private fun logStructureOnce() {
        if (!structureLogged) {
            synchronized(ChatClientLoggingAdvisor::class.java) {
                if (!structureLogged) {
                    log.info(
                        "LLM logging enabled. " +
                                "Requests/responses are logged with first {} and last {} characters, " +
                                "including model, duration and token usage (prompt/completion/total).",
                        HEAD_LEN,
                        TAIL_LEN
                    )
                    structureLogged = true
                }
            }
        }
    }

    /**
     * Обрезает текст для логов: первые HEAD_LEN и последние TAIL_LEN символов.
     */
    private fun shortenForLog(text: String): String {
        if (text.isBlank()) {
            return "<empty>"
        }
        if (text.length <= HEAD_LEN + TAIL_LEN) {
            return text
        }

        val head = text.substring(0, HEAD_LEN)
        val tail = text.substring(text.length - TAIL_LEN)

        return "$head ... $tail"
    }
}
