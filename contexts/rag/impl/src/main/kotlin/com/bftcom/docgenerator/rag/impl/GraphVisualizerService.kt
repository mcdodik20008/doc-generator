package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.impl.steps.QueryStep
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * Сервис для визуализации графа переходов между шагами обработки запроса.
 * Выводит ASCII-схему переходов в консоль при старте приложения.
 */
@Service
class GraphVisualizerService(
    private val steps: List<QueryStep>,
) {
    private val RESET = "\u001B[0m"
    private val GREEN = "\u001B[32m"
    private val YELLOW = "\u001B[33m"
    private val RED = "\u001B[31m"
    private val CYAN = "\u001B[36m"

    @EventListener(ApplicationReadyEvent::class)
    fun visualizeGraph() {
        val stepsMap = steps.associateBy { it.type }
        val sb = StringBuilder()

        sb.appendLine("\n")
        sb.appendLine("╔══════════════════════════════════════════════════════════╗")
        sb.appendLine("║          RAG ENGINE FLOW: STEP-BY-STEP TOPOLOGY          ║")
        sb.appendLine("╚══════════════════════════════════════════════════════════╝")

        fun render(
            type: ProcessingStepType,
            prefix: String = "",
            isLast: Boolean = true,
            transition: String = "",
            currentPath: Set<ProcessingStepType> = emptySet()
        ) {
            val step = stepsMap[type]
            val connector = if (prefix.isEmpty()) "• " else if (isLast) "└── " else "├── "

            // Определяем цвет узла
            val nodeColor = when (type) {
                ProcessingStepType.COMPLETED -> GREEN
                ProcessingStepType.FAILED -> RED
                ProcessingStepType.EXACT_SEARCH, ProcessingStepType.GRAPH_EXPANSION -> GREEN
                ProcessingStepType.VECTOR_SEARCH, ProcessingStepType.REWRITING -> YELLOW
                else -> RESET
            }

            val transitionLabel = if (transition.isNotEmpty()) " $CYAN◀── ($transition)$RESET" else ""

            // Собираем строку с цветами
            sb.append(".").append(prefix).append(connector)
                .append(nodeColor).append("[${type.description}]").append(RESET)
                .append(transitionLabel).append("\n")

            if (type in currentPath) return // Защита от циклов

            val transitions = step?.getTransitions()?.entries ?: emptyList()
            val nextPrefix = prefix + (if (prefix.isEmpty()) ".." else if (isLast) "...." else "│...")

            transitions.forEachIndexed { index, entry ->
                render(entry.value, nextPrefix, index == transitions.size - 1, entry.key, currentPath + type)
            }
        }

        render(ProcessingStepType.NORMALIZATION)
        sb.appendLine("────────────────────────────────────────────────────────────")

        // Выводим через небольшую паузу, чтобы не перемешаться с баннером Spring
        Thread {
            Thread.sleep(1500)
            System.out.println(sb.toString())
        }.start()
    }
}