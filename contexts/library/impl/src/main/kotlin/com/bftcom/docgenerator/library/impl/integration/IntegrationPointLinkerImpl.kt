package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointLinker
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Реализация линкера интеграционных точек.
 *
 * Создает Edge между методами приложения и интеграционными точками из библиотек.
 *
 * Пока упрощенная версия - создает виртуальные узлы для интеграционных точек
 * и связывает с ними методы приложения.
 */
@Service
class IntegrationPointLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val libraryNodeRepo: LibraryNodeRepository,
    private val edgeRepo: EdgeRepository,
    private val integrationPointService: IntegrationPointService,
    private val objectMapper: ObjectMapper,
) : IntegrationPointLinker {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun linkIntegrationPoints(application: Application): IntegrationPointLinker.IntegrationLinkResult {
        log.info("Linking integration points for application: {}", application.key)

        var httpEdgesCreated = 0
        var kafkaEdgesCreated = 0
        var camelEdgesCreated = 0
        val errors = mutableListOf<String>()

        val batchSize = com.bftcom.docgenerator.shared.config.SharedConstants.DEFAULT_BATCH_SIZE
        // Кэши для минимизации походов в БД внутри батча
        val libraryFqnCache = mutableMapOf<String, List<com.bftcom.docgenerator.domain.library.LibraryNode>>()
        val integrationCache = mutableMapOf<Long, Set<IntegrationPoint>>()
        val infraNodeCache = mutableMapOf<String, com.bftcom.docgenerator.domain.node.Node>()

        var pageIndex = 0
        while (true) {
            val page =
                nodeRepo.findPageAllByApplicationIdAndKindIn(
                    application.id!!,
                    setOf(NodeKind.METHOD),
                    PageRequest.of(pageIndex, batchSize),
                )
            val appMethods = page.content
            if (appMethods.isEmpty()) break

            val nodesToCreate = mutableListOf<com.bftcom.docgenerator.domain.node.Node>()
            val pendingEdges =
                mutableListOf<Triple<com.bftcom.docgenerator.domain.node.Node, com.bftcom.docgenerator.domain.node.Node, EdgeKind>>()

            appMethods.forEach { method ->
                // ЛОГ 1: Вход в метод
                val meta =
                    runCatching { objectMapper.convertValue(method.meta, NodeMeta::class.java) }
                        .onFailure { log.error("Failed to convert meta for method ${method.fqn}: ${it.message}") }
                        .getOrNull()

                val usages = meta?.rawUsages.orEmpty().filter { it.checkIsCall() }
                if (usages.isEmpty()) {
                    log.debug("No calls found in meta for method: {}", method.fqn)
                }

                usages.forEach { usage ->
                    val calledFqn = resolveLibraryFqn(usage, meta)

                    // ЛОГ 2: Результат резолва
                    if (calledFqn == null) {
                        log.warn("FQN not resolved for usage '{}' in method '{}'", usage, method.fqn)
                        return@forEach
                    }

                    // Проверяем, не является ли это вызовом внутри самого приложения
                    if (nodeRepo.findByApplicationIdAndFqn(application.id!!, calledFqn) != null) {
                        return@forEach
                    }

                    val libNodes =
                        libraryFqnCache.getOrPut(calledFqn) {
                            libraryNodeRepo.findAllByFqn(calledFqn)
                        }

                    if (libNodes.isEmpty()) {
                        log.debug("FQN {} not found in library_node table", calledFqn)
                        return@forEach
                    }

                    libNodes.forEach { libNode ->
                        val points =
                            integrationPointService.resolveIntegrationPointsTransitive(
                                libNode,
                                10,
                                integrationCache,
                                mutableSetOf(),
                            )

                        // ЛОГ 3: Найденные точки
                        if (points.isNotEmpty()) {
                            log.info("Found {} integration points for call {} -> {}", points.size, method.fqn, calledFqn)
                        }

                        points.forEach { point ->
                            val infraNode = getOrCreateInfraNode(point, application, infraNodeCache, nodesToCreate)
                            if (infraNode != null) {
                                pendingEdges.add(Triple(method, infraNode, edgeKindFor(point)))
                            }
                        }
                    }
                }
            }

            // Сохраняем новые инфраструктурные узлы
            if (nodesToCreate.isNotEmpty()) {
                log.info("Creating {} new infrastructure nodes", nodesToCreate.size)
                val savedNodes = nodeRepo.saveAll(nodesToCreate)
                savedNodes.forEach { infraNodeCache[it.fqn] = it }
            }

            // Создаем ребра
            pendingEdges.forEach { (src, dst, kind) ->
                val dstId = dst.id ?: infraNodeCache[dst.fqn]?.id
                if (src.id != null && dstId != null) {
                    edgeRepo.upsert(src.id!!, dstId, kind.name)
                    when (kind) {
                        EdgeKind.CALLS_HTTP -> httpEdgesCreated++
                        EdgeKind.PRODUCES, EdgeKind.CONSUMES -> kafkaEdgesCreated++
                        EdgeKind.CALLS_CAMEL -> camelEdgesCreated++
                        else -> {}
                    }
                }
            }

            if (!page.hasNext()) break
            pageIndex++
        }

        return IntegrationPointLinker.IntegrationLinkResult(httpEdgesCreated, kafkaEdgesCreated, camelEdgesCreated, errors)
    }

    private fun edgeKindFor(point: IntegrationPoint): EdgeKind =
        when (point) {
            is IntegrationPoint.HttpEndpoint -> EdgeKind.CALLS_HTTP
            is IntegrationPoint.KafkaTopic -> if (point.operation == "PRODUCE") EdgeKind.PRODUCES else EdgeKind.CONSUMES
            is IntegrationPoint.CamelRoute -> EdgeKind.CALLS_CAMEL
        }

    private fun getOrCreateInfraNode(
        point: IntegrationPoint,
        application: Application,
        cache: MutableMap<String, com.bftcom.docgenerator.domain.node.Node>,
        nodesToCreate: MutableList<com.bftcom.docgenerator.domain.node.Node>,
    ): com.bftcom.docgenerator.domain.node.Node? {
        val (fqn, kind, name) =
            when (point) {
                is IntegrationPoint.HttpEndpoint -> {
                    val url = point.url ?: "unknown"
                    val method = point.httpMethod ?: "UNKNOWN"
                    Triple("infra:http:$method:$url", NodeKind.ENDPOINT, "$method $url")
                }
                is IntegrationPoint.KafkaTopic -> {
                    val topic = point.topic ?: "unknown"
                    Triple("infra:kafka:topic:$topic", NodeKind.TOPIC, topic)
                }
                is IntegrationPoint.CamelRoute -> {
                    val uri = point.uri ?: "unknown"
                    Triple("infra:camel:uri:$uri", NodeKind.ENDPOINT, uri)
                }
            }

        cache[fqn]?.let { return it }

        val existing = nodeRepo.findByApplicationIdAndFqn(application.id!!, fqn)
        if (existing != null) {
            cache[fqn] = existing
            return existing
        }

        val newNode =
            com.bftcom.docgenerator.domain.node.Node(
                application = application,
                fqn = fqn,
                name = name,
                packageName = null,
                kind = kind,
                lang = Lang.other,
                parent = null,
                filePath = null,
                lineStart = null,
                lineEnd = null,
                sourceCode = null,
                docComment = null,
                signature = null,
                codeHash = null,
                meta =
                    mapOf(
                        "synthetic" to true,
                        "origin" to "linker",
                    ),
            )

        cache[fqn] = newNode
        nodesToCreate.add(newNode)
        return newNode
    }

    private fun resolveLibraryFqn(
        usage: RawUsage,
        meta: NodeMeta?,
    ): String? {
        val imports = meta?.imports.orEmpty()
        val ownerFqn = meta?.ownerFqn

        return when (usage) {
            is RawUsage.Simple -> {
                ownerFqn?.let { "$it.${usage.name}" }
                    ?: imports.find { it.endsWith(".${usage.name}") }
                    ?: imports
                        .find {
                            it
                                .split('.')
                                .last()
                                .firstOrNull()
                                ?.isUpperCase() == true
                        }?.let { "$it.${usage.name}" }
                    ?: if (usage.name.contains('.')) usage.name else null
            }
            is RawUsage.Dot -> {
                val receiverFqn =
                    if (usage.receiver.contains('.')) {
                        usage.receiver
                    } else if (usage.receiver.firstOrNull()?.isUpperCase() == true) {
                        imports.find { it.endsWith(".${usage.receiver}") }
                    } else {
                        ownerFqn
                    }
                receiverFqn?.let { "$it.${usage.member}" }
            }
        }
    }
}
