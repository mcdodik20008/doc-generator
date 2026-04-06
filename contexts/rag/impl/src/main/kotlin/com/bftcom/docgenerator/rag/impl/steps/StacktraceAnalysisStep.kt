package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Анализирует распарсенный стектрейс: ищет соответствие фреймов узлам графа,
 * расширяет граф вокруг корневой причины и формирует диагностический текст.
 */
@Component
class StacktraceAnalysisStep(
    private val nodeRepository: NodeRepository,
    private val edgeRepository: EdgeRepository,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.STACKTRACE_ANALYSIS

    companion object {
        private val EXPANSION_EDGE_KINDS =
            setOf(
                EdgeKind.CALLS_CODE,
                EdgeKind.CALLS_HTTP,
                EdgeKind.READS,
                EdgeKind.WRITES,
                EdgeKind.DEPENDS_ON,
            )
    }

    override fun execute(context: QueryProcessingContext): StepResult {
        val appFrames =
            context
                .getMetadata<List<*>>(QueryMetadataKeys.STACKTRACE_APP_FRAMES)
                ?.filterIsInstance<StackFrame>()
                .orEmpty()
        val exceptionType = context.getMetadata<String>(QueryMetadataKeys.STACKTRACE_EXCEPTION_TYPE) ?: ""
        val exceptionMessage = context.getMetadata<String>(QueryMetadataKeys.STACKTRACE_EXCEPTION_MESSAGE) ?: ""
        val rootCauseFrame = context.getMetadata<StackFrame>(QueryMetadataKeys.STACKTRACE_ROOT_CAUSE_FRAME)
        val appId = context.getMetadata<Long>(QueryMetadataKeys.APPLICATION_ID)

        // Маппим фреймы на узлы графа
        val frameMappings = mutableListOf<FrameMapping>()
        val exactNodes = mutableListOf<Node>()

        for (frame in appFrames) {
            val node =
                if (appId != null) {
                    findNodeForFrame(appId, frame)
                } else {
                    null
                }
            frameMappings.add(FrameMapping(frame, node))
            if (node != null) exactNodes.add(node)
        }

        // Расширяем граф вокруг root cause
        val neighborNodes = mutableListOf<Node>()
        var graphRelationsText = ""

        val rootCauseNode =
            frameMappings
                .find {
                    it.frame == rootCauseFrame && it.node != null
                }?.node

        if (rootCauseNode != null) {
            val expansionResult = expandRootCause(rootCauseNode)
            neighborNodes.addAll(expansionResult.neighbors)
            graphRelationsText = expansionResult.relationsText
        }

        // Формируем текст анализа
        val analysisText =
            buildAnalysisText(
                exceptionType,
                exceptionMessage,
                frameMappings,
                rootCauseNode,
                graphRelationsText,
            )

        val updatedContext =
            context
                .setMetadata(QueryMetadataKeys.STACKTRACE_ANALYSIS_TEXT, analysisText)
                .apply {
                    if (exactNodes.isNotEmpty()) {
                        setMetadata(QueryMetadataKeys.EXACT_NODES, exactNodes.distinctBy { it.id })
                    }
                    if (neighborNodes.isNotEmpty()) {
                        setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, neighborNodes.distinctBy { it.id })
                    }
                    if (graphRelationsText.isNotBlank()) {
                        setMetadata(QueryMetadataKeys.GRAPH_RELATIONS_TEXT, graphRelationsText)
                    }
                }.addStep(
                    ProcessingStep(
                        advisorName = "StacktraceAnalysisStep",
                        input = context.currentQuery.take(200),
                        output = "Маппинг: ${frameMappings.count {
                            it.node != null
                        }}/${frameMappings.size} фреймов, соседей: ${neighborNodes.size}",
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )

        val transitionKey = if (exactNodes.isNotEmpty()) "SUCCESS" else "NO_NODES"

        log.info(
            "STACKTRACE_ANALYSIS: mapped={}/{}, neighbors={}, rootCause={}",
            frameMappings.count { it.node != null },
            frameMappings.size,
            neighborNodes.size,
            rootCauseNode?.fqn,
        )
        return StepResult(context = updatedContext, transitionKey = transitionKey)
    }

    private fun findNodeForFrame(
        appId: Long,
        frame: StackFrame,
    ): Node? {
        val nodes =
            nodeRepository.findByApplicationIdAndClassNameAndMethodNameIgnoreCase(
                applicationId = appId,
                className = frame.className,
                methodName = frame.methodName,
                methodKind = NodeKind.METHOD,
            )
        return nodes.firstOrNull()
    }

    private fun expandRootCause(rootNode: Node): ExpansionResult {
        val nodeId = rootNode.id ?: return ExpansionResult(emptyList(), "")

        val outgoing =
            edgeRepository
                .findAllBySrcId(nodeId)
                .filter { it.kind in EXPANSION_EDGE_KINDS }
        val incoming =
            edgeRepository
                .findAllByDstId(nodeId)
                .filter { it.kind == EdgeKind.CALLS_CODE }

        val allEdges = outgoing + incoming
        val neighborIds = allEdges.flatMap { listOfNotNull(it.src.id, it.dst.id) }.toSet() - nodeId
        val neighbors =
            if (neighborIds.isNotEmpty()) {
                nodeRepository.findAllByIdIn(neighborIds)
            } else {
                emptyList()
            }

        val allNodes = (neighbors + rootNode).associateBy { it.id }
        val relationsText =
            if (allEdges.isNotEmpty()) {
                buildString {
                    appendLine("СВЯЗИ МЕТОДА С ОШИБКОЙ:")
                    for (edge in allEdges) {
                        val srcLabel = allNodes[edge.src.id]?.let { it.name ?: it.fqn } ?: "Node#${edge.src.id}"
                        val dstLabel = allNodes[edge.dst.id]?.let { it.name ?: it.fqn } ?: "Node#${edge.dst.id}"
                        appendLine("- [$srcLabel] ${relationVerb(edge.kind)} [$dstLabel]")
                    }
                }
            } else {
                ""
            }

        return ExpansionResult(neighbors, relationsText)
    }

    private fun buildAnalysisText(
        exceptionType: String,
        exceptionMessage: String,
        frameMappings: List<FrameMapping>,
        rootCauseNode: Node?,
        graphRelationsText: String,
    ): String =
        buildString {
            if (exceptionType.isNotBlank()) {
                appendLine("## Исключение: ${exceptionType.substringAfterLast('.')}")
                if (exceptionMessage.isNotBlank()) {
                    appendLine("Сообщение: $exceptionMessage")
                }
                appendLine()
            }

            if (rootCauseNode != null) {
                appendLine("## Место ошибки")
                appendLine(
                    "Класс: ${rootCauseNode.parent?.name ?: rootCauseNode.fqn.substringBeforeLast(
                        '.',
                    )} (${rootCauseNode.parent?.fqn ?: ""})",
                )
                appendLine("Метод: ${rootCauseNode.name ?: rootCauseNode.fqn.substringAfterLast('.')}")
                val rootFrame = frameMappings.find { it.node?.id == rootCauseNode.id }?.frame
                if (rootFrame?.lineNumber != null) {
                    appendLine("Строка: ${rootFrame.lineNumber}")
                }
                if (!rootCauseNode.sourceCode.isNullOrBlank()) {
                    val code = rootCauseNode.sourceCode!!
                    val truncated = if (code.length > 2000) code.take(2000) + "\n// ... [код обрезан]" else code
                    appendLine("Исходный код:\n```\n$truncated\n```")
                }
                appendLine()
            }

            if (frameMappings.isNotEmpty()) {
                appendLine("## Цепочка вызовов")
                for ((i, mapping) in frameMappings.withIndex()) {
                    val frame = mapping.frame
                    val node = mapping.node
                    val marker =
                        if (node != null) {
                            val isRoot = node.id == rootCauseNode?.id
                            "Node#${node.id} ✓" + if (isRoot) " (ROOT CAUSE)" else ""
                        } else {
                            "не найден в графе"
                        }
                    val lineInfo = if (frame.lineNumber != null) " (строка ${frame.lineNumber})" else ""
                    appendLine("${i + 1}. ${frame.className}.${frame.methodName}()$lineInfo → $marker")
                }
                appendLine()
            }

            if (graphRelationsText.isNotBlank()) {
                appendLine("## Граф контекст (метод с ошибкой)")
                appendLine(graphRelationsText)
            }
        }.trim()

    private fun relationVerb(kind: EdgeKind): String =
        when (kind) {
            EdgeKind.CALLS_CODE -> "вызывает"
            EdgeKind.CALLS_HTTP -> "вызывает HTTP"
            EdgeKind.DEPENDS_ON -> "зависит от"
            EdgeKind.READS -> "читает из"
            EdgeKind.WRITES -> "пишет в"
            else -> "связан с"
        }

    override fun getTransitions(): Map<String, ProcessingStepType> =
        linkedMapOf(
            "SUCCESS" to ProcessingStepType.VECTOR_SEARCH,
            "NO_NODES" to ProcessingStepType.VECTOR_SEARCH,
        )

    private data class FrameMapping(
        val frame: StackFrame,
        val node: Node?,
    )

    private data class ExpansionResult(
        val neighbors: List<Node>,
        val relationsText: String,
    )
}
