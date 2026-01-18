package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ExactSearchStep(
    private val nodeRepository: NodeRepository,
    private val applicationRepository: ApplicationRepository,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.EXACT_SEARCH

    override fun execute(context: QueryProcessingContext): StepResult {
        val extractionResult = context.getMetadata<Map<*, *>>(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)
        val className = (extractionResult?.get("className") as? String)?.takeIf { it.isNotBlank() }
        val methodName = (extractionResult?.get("methodName") as? String)?.takeIf { it.isNotBlank() }

        if (className.isNullOrBlank() && methodName.isNullOrBlank()) {
            val updatedContext = context.addStep(
                ProcessingStep(
                    advisorName = "ExactSearchStep",
                    input = context.currentQuery,
                    output = "Нет извлеченных классов/методов, переходим к VECTOR_SEARCH",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )
            return StepResult(ProcessingStepType.VECTOR_SEARCH, updatedContext)
        }

        val foundNodes = findNodes(className, methodName)
        val updatedContext = if (foundNodes.isNotEmpty()) {
            context
                .setMetadata(QueryMetadataKeys.EXACT_NODES, foundNodes)
                .addStep(
                    ProcessingStep(
                        advisorName = "ExactSearchStep",
                        input = context.currentQuery,
                        output = "Найдено узлов: ${foundNodes.size}",
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )
        } else {
            context.addStep(
                ProcessingStep(
                    advisorName = "ExactSearchStep",
                    input = context.currentQuery,
                    output = "Узлы не найдены",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )
        }

        val nextStep = if (foundNodes.isNotEmpty()) {
            ProcessingStepType.GRAPH_EXPANSION
        } else {
            ProcessingStepType.VECTOR_SEARCH
        }

        log.info("EXACT_SEARCH: class='{}', method='{}', nodes={}", className, methodName, foundNodes.size)
        return StepResult(nextStep, updatedContext)
    }

    private fun findNodes(className: String?, methodName: String?): List<Node> {
        val foundNodes = mutableListOf<Node>()

        val applications = applicationRepository.findAll()
        if (applications.isEmpty()) {
            log.warn("Не найдено приложений для поиска узлов (Application table empty)")
            return foundNodes
        }

        if (className != null && methodName != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                    applicationId = appId,
                    className = className,
                    methodName = methodName,
                    methodKind = NodeKind.METHOD,
                )
                foundNodes.addAll(nodes)
            }
        } else if (className != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndClassName(
                    applicationId = appId,
                    className = className,
                    classKinds = setOf(NodeKind.CLASS, NodeKind.INTERFACE),
                )
                foundNodes.addAll(nodes)
            }
        } else if (methodName != null) {
            for (app in applications) {
                val appId = app.id ?: continue
                val nodes = nodeRepository.findByApplicationIdAndMethodName(
                    applicationId = appId,
                    methodName = methodName,
                    methodKind = NodeKind.METHOD,
                )
                foundNodes.addAll(nodes)
            }
        }

        return foundNodes.distinctBy { it.id }
    }
}
