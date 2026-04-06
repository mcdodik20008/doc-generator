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
import org.springframework.data.domain.PageRequest
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
            // Проверяем, есть ли гипотетические имена для поиска
            val hypotheticalNames =
                context
                    .getMetadata<List<*>>(QueryMetadataKeys.HYPOTHETICAL_NAMES)
                    ?.filterIsInstance<String>()

            if (hypotheticalNames.isNullOrEmpty()) {
                val updatedContext =
                    context.addStep(
                        ProcessingStep(
                            advisorName = "ExactSearchStep",
                            input = context.currentQuery,
                            output = "Нет извлеченных классов/методов, переходим к REWRITING",
                            stepType = type,
                            status = ProcessingStepStatus.SUCCESS,
                        ),
                    )
                return StepResult(
                    context = updatedContext,
                    transitionKey = "NO_DATA",
                )
            }
        }

        val appIdFromContext = context.getMetadata<Long>(QueryMetadataKeys.APPLICATION_ID)
        val foundNodes = findNodes(className, methodName, appIdFromContext).toMutableList()

        // Если не нашли по точным именам — пробуем гипотетические имена
        if (foundNodes.isEmpty()) {
            val hypotheticalNames =
                context
                    .getMetadata<List<*>>(QueryMetadataKeys.HYPOTHETICAL_NAMES)
                    ?.filterIsInstance<String>()
            if (!hypotheticalNames.isNullOrEmpty()) {
                val appIds = getApplicationIds(appIdFromContext)
                for (name in hypotheticalNames) {
                    for (appId in appIds) {
                        val byFqn =
                            nodeRepository.findByApplicationIdAndFqnContaining(
                                applicationId = appId,
                                fqnPattern = name,
                                pageable = PageRequest.of(0, 3),
                            )
                        foundNodes.addAll(byFqn)
                    }
                    if (foundNodes.isNotEmpty()) break
                }
            }
        }

        val distinctNodes = foundNodes.distinctBy { it.id }
        val updatedContext =
            if (distinctNodes.isNotEmpty()) {
                context
                    .setMetadata(QueryMetadataKeys.EXACT_NODES, distinctNodes)
                    .addStep(
                        ProcessingStep(
                            advisorName = "ExactSearchStep",
                            input = context.currentQuery,
                            output = "Найдено узлов: ${distinctNodes.size}",
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

        val transitionKey =
            if (distinctNodes.isNotEmpty()) {
                "HAS_DATA"
            } else {
                "NO_DATA"
            }

        log.info("EXACT_SEARCH: class='{}', method='{}', nodes={}", className, methodName, distinctNodes.size)
        return StepResult(
            context = updatedContext,
            transitionKey = transitionKey,
        )
    }

    private fun getApplicationIds(appIdFromContext: Long?): List<Long> =
        if (appIdFromContext != null) {
            listOf(appIdFromContext)
        } else {
            applicationRepository.findAll().mapNotNull { it.id }
        }

    private fun findNodes(
        className: String?,
        methodName: String?,
        appIdFromContext: Long?,
    ): List<Node> {
        val foundNodes = mutableListOf<Node>()
        val appIds = getApplicationIds(appIdFromContext)

        if (appIds.isEmpty()) {
            log.warn("Не найдено приложений для поиска узлов (Application table empty)")
            return foundNodes
        }

        if (className != null && methodName != null) {
            for (appId in appIds) {
                val nodes =
                    nodeRepository.findByApplicationIdAndClassNameAndMethodNameIgnoreCase(
                        applicationId = appId,
                        className = className,
                        methodName = methodName,
                        methodKind = NodeKind.METHOD,
                    )
                foundNodes.addAll(nodes)
            }
        } else if (className != null) {
            for (appId in appIds) {
                val nodes =
                    nodeRepository.findByApplicationIdAndClassNameIgnoreCase(
                        applicationId = appId,
                        className = className,
                        classKinds = setOf(NodeKind.CLASS, NodeKind.INTERFACE),
                    )
                foundNodes.addAll(nodes)
            }
        } else if (methodName != null) {
            for (appId in appIds) {
                val nodes =
                    nodeRepository.findByApplicationIdAndMethodNameIgnoreCase(
                        applicationId = appId,
                        methodName = methodName,
                        methodKind = NodeKind.METHOD,
                    )
                foundNodes.addAll(nodes)
            }
        }

        return foundNodes.distinctBy { it.id }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> =
        linkedMapOf(
            "HAS_DATA" to ProcessingStepType.GRAPH_EXPANSION,
            "NO_DATA" to ProcessingStepType.REWRITING,
        )
}
