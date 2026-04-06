package com.bftcom.docgenerator.rag.impl.research

import com.bftcom.docgenerator.rag.api.DeepResearchResponse
import com.bftcom.docgenerator.rag.api.DeepResearchService
import com.bftcom.docgenerator.rag.api.RagSource
import com.bftcom.docgenerator.rag.api.ResearchEvent
import com.bftcom.docgenerator.rag.api.ResearchEventType
import com.bftcom.docgenerator.rag.api.ResearchProgressCallback
import com.bftcom.docgenerator.rag.api.ResearchStep
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class DeepResearchServiceImpl(
    @Qualifier("researchChatClient") private val chatClient: ChatClient,
    private val tools: ResearchTools,
    private val promptBuilder: ReActPromptBuilder,
    @Value("\${docgen.rag.deep-research.max-iterations:8}") private val maxIterations: Int = 8,
    @Value("\${docgen.rag.deep-research.iteration-timeout-seconds:30}") private val iterationTimeoutSeconds: Long = 30,
    @Value("\${docgen.rag.deep-research.total-timeout-seconds:180}") private val totalTimeoutSeconds: Long = 180,
) : DeepResearchService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun research(query: String, sessionId: String, applicationId: Long?): DeepResearchResponse {
        return researchWithProgress(query, sessionId, applicationId) { /* no-op callback */ }
    }

    override fun researchWithProgress(
        query: String,
        sessionId: String,
        applicationId: Long?,
        callback: ResearchProgressCallback,
    ): DeepResearchResponse {
        val startTime = System.currentTimeMillis()
        log.info("Deep research started: sessionId={}, appId={}, query='{}'", sessionId, applicationId, query.take(100))

        val history = mutableListOf<ReActTurn>()
        val steps = mutableListOf<ResearchStep>()
        val collectedSources = mutableListOf<RagSource>()
        var finalAnswer: String? = null

        for (iteration in 1..maxIterations) {
            val iterStart = System.currentTimeMillis()
            val elapsed = iterStart - startTime

            // Check total timeout
            if (elapsed > totalTimeoutSeconds * 1000) {
                log.warn("Deep research total timeout reached at iteration {}", iteration)
                callback.onEvent(ResearchEvent(ResearchEventType.ERROR, iteration, "Превышен общий таймаут ($totalTimeoutSeconds с)"))
                finalAnswer = buildTimeoutAnswer(query, history)
                break
            }

            try {
                // 1. Call LLM
                val systemPrompt = promptBuilder.buildSystemPrompt()
                val userPrompt = promptBuilder.buildUserPrompt(query, history)

                val llmResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content() ?: ""

                log.debug("Iteration {} LLM response length: {}", iteration, llmResponse.length)

                // 2. Parse response
                val parsed = parseReActResponse(llmResponse)

                // 3. Emit thinking event
                callback.onEvent(ResearchEvent(ResearchEventType.THINKING, iteration, parsed.thought))

                if (parsed.finalAnswer != null) {
                    // Final answer reached
                    finalAnswer = parsed.finalAnswer
                    val iterDuration = System.currentTimeMillis() - iterStart
                    steps.add(ResearchStep(iteration, parsed.thought, null, null, null, iterDuration))
                    callback.onEvent(ResearchEvent(ResearchEventType.ANSWER, iteration, finalAnswer))
                    log.info("Deep research completed with Final Answer at iteration {}", iteration)
                    break
                }

                if (parsed.action != null && parsed.actionInput != null) {
                    // 4. Emit action event
                    callback.onEvent(ResearchEvent(ResearchEventType.ACTION, iteration, "${parsed.action}: ${parsed.actionInput}"))

                    // 5. Execute tool
                    val observation = executeTool(parsed.action, parsed.actionInput, applicationId)

                    // 6. Emit observation event
                    callback.onEvent(ResearchEvent(ResearchEventType.OBSERVATION, iteration, observation))

                    // 7. Collect sources from search results
                    collectSources(parsed.action, observation, collectedSources)

                    val turn = ReActTurn(
                        thought = parsed.thought,
                        action = parsed.action,
                        actionInput = parsed.actionInput,
                        observation = observation,
                    )
                    history.add(turn)

                    val iterDuration = System.currentTimeMillis() - iterStart
                    steps.add(ResearchStep(iteration, parsed.thought, parsed.action, parsed.actionInput, observation, iterDuration))
                } else {
                    // No action and no final answer — force stop
                    log.warn("Iteration {} produced no action and no final answer, forcing stop", iteration)
                    val iterDuration = System.currentTimeMillis() - iterStart
                    steps.add(ResearchStep(iteration, parsed.thought, null, null, null, iterDuration))
                    finalAnswer = parsed.thought
                    break
                }
            } catch (e: Exception) {
                log.error("Deep research iteration {} failed: {}", iteration, e.message, e)
                callback.onEvent(ResearchEvent(ResearchEventType.ERROR, iteration, "Ошибка: ${e.message}"))
                val iterDuration = System.currentTimeMillis() - iterStart
                steps.add(ResearchStep(iteration, "Ошибка: ${e.message}", null, null, null, iterDuration))

                // Try to produce an answer from what we have so far
                if (history.isNotEmpty()) {
                    finalAnswer = buildTimeoutAnswer(query, history)
                }
                break
            }
        }

        // If max iterations reached without final answer
        if (finalAnswer == null) {
            log.warn("Deep research exhausted max iterations ({})", maxIterations)
            finalAnswer = buildTimeoutAnswer(query, history)
            callback.onEvent(ResearchEvent(ResearchEventType.ANSWER, maxIterations, finalAnswer))
        }

        val totalDuration = System.currentTimeMillis() - startTime
        log.info("Deep research finished: {} iterations, {}ms total", steps.size, totalDuration)

        return DeepResearchResponse(
            answer = finalAnswer,
            thinkingSteps = steps,
            sources = collectedSources.distinctBy { it.id },
            iterationsUsed = steps.size,
            totalDurationMs = totalDuration,
        )
    }

    private fun executeTool(action: String, input: String, appId: Long?): String {
        return try {
            when (action.lowercase().trim()) {
                "search_nodes" -> tools.searchNodes(input, appId)
                "search_code" -> tools.searchCode(input, appId)
                "get_node_details" -> tools.getNodeDetails(input, appId)
                "explore_graph" -> tools.exploreGraph(input, appId)
                "find_paths" -> tools.findPaths(input, appId)
                "list_overview" -> tools.listOverview(input, appId)
                else -> "Неизвестный инструмент: $action. Доступные: search_nodes, search_code, get_node_details, explore_graph, find_paths, list_overview"
            }
        } catch (e: Exception) {
            log.error("Tool execution failed: action={}, input={}, error={}", action, input, e.message, e)
            "Ошибка выполнения инструмента $action: ${e.message}"
        }
    }

    private fun collectSources(action: String, observation: String, sources: MutableList<RagSource>) {
        // Extract node IDs from search results to build sources
        val idPattern = Regex("""\[id=(\d+)]""")
        val matches = idPattern.findAll(observation)
        for (match in matches) {
            val id = match.groupValues[1]
            if (sources.none { it.id == id }) {
                sources.add(
                    RagSource(
                        id = id,
                        content = observation.take(200),
                        metadata = mapOf("tool" to action),
                        similarity = 1.0,
                    ),
                )
            }
        }
    }

    private fun buildTimeoutAnswer(query: String, history: List<ReActTurn>): String {
        if (history.isEmpty()) {
            return "Не удалось провести исследование по запросу: $query"
        }

        return buildString {
            appendLine("На основе проведённого исследования:")
            appendLine()
            for (turn in history) {
                appendLine("**Шаг:** ${turn.thought}")
                if (turn.observation != null) {
                    appendLine("**Найдено:** ${turn.observation.take(500)}")
                }
                appendLine()
            }
            appendLine("---")
            appendLine("*Примечание: исследование было прервано по таймауту или лимиту итераций. Результаты могут быть неполными.*")
        }
    }

    /**
     * Парсит ответ LLM в формате ReAct.
     * Ожидаемые форматы:
     * 1) Thought: ... / Action: ... / Action Input: ...
     * 2) Thought: ... / Final Answer: ...
     */
    internal fun parseReActResponse(text: String): ReActParsed {
        val lines = text.lines()

        var thought = ""
        var action: String? = null
        var actionInput: String? = null
        var finalAnswer: String? = null

        var currentSection: String? = null
        val sectionContent = StringBuilder()

        fun flushSection() {
            val content = sectionContent.toString().trim()
            when (currentSection) {
                "thought" -> thought = content
                "action" -> action = content
                "action_input" -> actionInput = content
                "final_answer" -> finalAnswer = content
            }
            sectionContent.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("Thought:", ignoreCase = true) -> {
                    flushSection()
                    currentSection = "thought"
                    sectionContent.appendLine(trimmed.removePrefix("Thought:").removePrefix("thought:").trim())
                }
                trimmed.startsWith("Action Input:", ignoreCase = true) -> {
                    flushSection()
                    currentSection = "action_input"
                    sectionContent.appendLine(trimmed.removePrefix("Action Input:").removePrefix("action input:").trim())
                }
                trimmed.startsWith("Action:", ignoreCase = true) -> {
                    flushSection()
                    currentSection = "action"
                    sectionContent.appendLine(trimmed.removePrefix("Action:").removePrefix("action:").trim())
                }
                trimmed.startsWith("Final Answer:", ignoreCase = true) -> {
                    flushSection()
                    currentSection = "final_answer"
                    sectionContent.appendLine(trimmed.removePrefix("Final Answer:").removePrefix("final answer:").trim())
                }
                else -> {
                    if (currentSection != null) {
                        sectionContent.appendLine(trimmed)
                    }
                }
            }
        }
        flushSection()

        // Fallback: если LLM не следовала формату, используем весь текст как thought
        if (thought.isBlank() && finalAnswer == null && action == null) {
            thought = text.trim()
        }

        return ReActParsed(
            thought = thought,
            action = action,
            actionInput = actionInput,
            finalAnswer = finalAnswer,
        )
    }
}

data class ReActParsed(
    val thought: String,
    val action: String? = null,
    val actionInput: String? = null,
    val finalAnswer: String? = null,
)
