package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.api.dto.ChunkBuildRequest
import com.bftcom.docgenerator.api.dto.ChunkBuildStatusDto
import com.bftcom.docgenerator.chunking.api.ChunkBuildOrchestrator
import com.bftcom.docgenerator.chunking.api.ChunkRunStore
import com.bftcom.docgenerator.chunking.api.ChunkStrategy
import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.model.ChunkPlan
import com.bftcom.docgenerator.chunking.model.ChunkRunHandle
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import kotlin.math.max

@Service
class ChunkBuildOrchestratorImpl(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val strategies: Map<String, ChunkStrategy>,
    private val chunkWriter: ChunkWriter,
    private val runStore: ChunkRunStore,
) : ChunkBuildOrchestrator {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun start(req: ChunkBuildRequest): ChunkRunHandle {
        val strategy = strategies[req.strategy] ?: error("Unknown strategy: ${req.strategy}")
        val run = runStore.create(req.applicationId, req.strategy)

        MDC.put("runId", run.runId)
        MDC.put("appId", req.applicationId.toString())
        MDC.put("strategy", req.strategy)

        val t0 = Instant.now()
        var processed = 0L
        var written = 0L
        var skipped = 0L
        var pages = 0
        val pageSize = max(50, req.batchSize)

        try {
            runStore.markRunning(run.runId)

            log.info(
                "Chunks run START (applicationId={}, strategy={}, dryRun={}, limitNodes={}, batchSize={}, includeKinds={}, withEdgesRelations={})",
                req.applicationId,
                req.strategy,
                req.dryRun,
                req.limitNodes,
                pageSize,
                req.includeKinds,
                req.withEdgesRelations,
            )

            val kindsFilter: Set<NodeKind>? =
                req.includeKinds
                    ?.mapNotNull { raw ->
                        try {
                            NodeKind.valueOf(raw.uppercase())
                        } catch (e: IllegalArgumentException) {
                            log.warn("Unknown NodeKind in includeKinds: {}", raw)
                            null
                        }
                    }?.toSet()
                    ?.takeIf { it.isNotEmpty() }
            var page = 0

            while (true) {
                val pageReq = PageRequest.of(page, pageSize, Sort.by("id").ascending())
                val pageData =
                    if (kindsFilter.isNullOrEmpty()) {
                        nodeRepo.findAllByApplicationId(req.applicationId, pageReq)
                    } else {
                        nodeRepo.findPageAllByApplicationIdAndKindIn(req.applicationId, kindsFilter, pageReq)
                    }

                val nodes = pageData.toList()
                if (nodes.isEmpty()) break

                pages++
                log.debug(
                    "Fetched page={}, size={}",
                    page,
                    nodes.size,
                )

                // Чтобы не ловить N+1 — читаем рёбра батчем для всех узлов страницы:
                val edgesBySrc: Map<Long, List<Edge>> =
                    if (req.withEdgesRelations) {
                        val ids = nodes.mapNotNull { it.id }
                        if (ids.isEmpty()) {
                            emptyMap()
                        } else {
                            edgeRepo.findAllBySrcIdIn(ids).groupBy { it.src!!.id!! }
                        }
                    } else {
                        emptyMap()
                    }

                // Строим планы и сохраняем пачками (на узел несколько планов ок):
                val plansBuffer = mutableListOf<ChunkPlan>()

                for (n in nodes) {
                    if (req.limitNodes != null && processed >= req.limitNodes) break

                    processed++
                    if (processed % 100L == 1L) {
                        log.debug("Processing node#{} fqn={} kind={}", processed, n.fqn, n.kind)
                    }

                    val edges = if (req.withEdgesRelations) edgesBySrc[n.id!!].orEmpty() else emptyList()
                    val plan = strategy.buildChunks(n, edges)

                    if (req.dryRun) {
                        skipped += plan.size.toLong()
                        if (plan.isNotEmpty()) {
                            log.trace("DRY-RUN node fqn={} → {} chunks planned (skipped)", n.fqn, plan.size)
                        }
                        continue
                    }

                    plansBuffer += plan

                    // защитимся от переполнения памяти при огромных графах:
                    if (plansBuffer.size >= 1000) {
                        val res = chunkWriter.savePlan(plansBuffer.toList())
                        written += res.written
                        skipped += res.skipped
                        plansBuffer.clear()
                    }
                }

                if (!req.dryRun && plansBuffer.isNotEmpty()) {
                    val res = chunkWriter.savePlan(plansBuffer.toList())
                    written += res.written
                    skipped += res.skipped
                    plansBuffer.clear()
                }

                if (req.limitNodes != null && processed >= req.limitNodes) {
                    log.debug("Limit reached: processedNodes={}", processed)
                    break
                }
                page++
            }

            val dt = Duration.between(t0, Instant.now())
            val rate = if (dt.toMillis() > 0) processed * 1000.0 / dt.toMillis() else 0.0

            runStore.markCompleted(run.runId, processed, written, skipped)
            log.info(
                "Chunks run DONE: processedNodes={}, writtenChunks={}, skippedChunks={}, pages={}, duration={} ms, rate={} nodes/s",
                processed,
                written,
                skipped,
                pages,
                dt.toMillis(),
                "%.2f".format(rate),
            )
        } catch (e: Exception) {
            runStore.markFailed(run.runId, e)
            log.error(
                "Chunks run FAILED: processedNodes={}, writtenChunks={}, skippedChunks={}",
                processed,
                written,
                skipped,
                e,
            )
            throw e
        } finally {
            MDC.clear()
        }

        return ChunkRunHandle(runId = run.runId, startedAt = run.startedAt)
    }

    override fun status(runId: String) = runStore.status(runId)
}
