package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Service
class NodeDocFillerScheduler(
    txManager: PlatformTransactionManager,
    private val nodeRepo: NodeRepository,
    private val generator: NodeDocGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    @Value("\${docgen.nodedoc.batch-size:10}")
    private var batchSize: Int = 10

    @Value("\${docgen.nodedoc.locale:ru}")
    private var locale: String = "ru"

    @Value("\${docgen.nodedoc.methods.random:false}")
    private var randomMethods: Boolean = false

    @Value("\${docgen.nodedoc.skip.factor:0.01}")
    private var skipFactor: Double = 0.01

    @Value("\${docgen.nodedoc.skip.min:3}")
    private var skipMin: Int = 3

    @Value("\${docgen.nodedoc.skip.max:100}")
    private var skipMax: Int = 100

    private val skipCounts = ConcurrentHashMap<Long, Int>()
    private val appNodeCounts = ConcurrentHashMap<Long, Long>()
    private val appNodeCountsAccessTime = ConcurrentHashMap<Long, AtomicLong>()
    
    @Value("\${docgen.nodedoc.cache.ttl-ms:3600000}")
    private var cacheTtlMs: Long = 3600000 // 1 hour default

    @PostConstruct
    fun validateConfiguration() {
        require(batchSize in 1..10000) {
            "docgen.nodedoc.batch-size must be between 1 and 10000, but was $batchSize"
        }
        log.info("NodeDocFillerScheduler initialized with batchSize=$batchSize, locale=$locale")
    }

    @Scheduled(fixedDelayString = "\${docgen.nodedoc.poll-ms:5000}")
    fun poll() {
        // bottom-up order
        val methodsSelected =
            processBatch("METHOD") {
                if (randomMethods) {
                    nodeRepo.lockNextMethodsWithoutDocRandom(locale, batchSize)
                } else {
                    nodeRepo.lockNextMethodsWithoutDoc(locale, batchSize)
                }
            }
        if (methodsSelected > 0) return

        val typesSelected = processBatch("TYPE") { nodeRepo.lockNextTypesWithoutDoc(locale, batchSize) }
        if (typesSelected > 0) return

        val packagesSelected = processBatch("PACKAGE") { nodeRepo.lockNextPackagesWithoutDoc(locale, batchSize) }
        if (packagesSelected > 0) return

        processBatch("MODULE/REPO") { nodeRepo.lockNextModulesAndReposWithoutDoc(locale, batchSize) }
    }

    private fun processBatch(label: String, loader: () -> List<Node>): Int {
        val batch = tx.execute { loader() } ?: return 0
        if (batch.isEmpty()) return 0
        log.info("nodedoc: generating {} items for {}", batch.size, label)
        
        val toStore = mutableListOf<Pair<Long, NodeDocGenerator.GeneratedDoc>>()
        val skipped = mutableListOf<Pair<Long, String>>()
        var failed = 0
        
        for (n in batch) {
            val nodeId = n.id ?: continue
            try {
                val allowMissingDeps = shouldAllowMissingDeps(n)
                val generated = generator.generate(n, locale, allowMissingDeps)
                if (generated == null) {
                    if (!allowMissingDeps) {
                        val next = skipCounts.merge(nodeId, 1, Int::plus) ?: 1
                        val maxSkips = maxSkipsFor(n)
                        skipped.add(nodeId to "skip=$next/$maxSkips")
                    } else {
                        skipped.add(nodeId to "forced")
                    }
                    continue
                }
                skipCounts.remove(nodeId)
                toStore.add(nodeId to generated)
            } catch (t: Throwable) {
                failed++
                log.warn("nodedoc: failed for nodeId={} fqn={}: {}", nodeId, n.fqn, t.message, t)
            }
        }
        
        // Batch store all generated docs in a single transaction
        if (toStore.isNotEmpty()) {
            tx.execute {
                for ((nodeId, generated) in toStore) {
                    generator.store(nodeId, locale, generated)
                }
            }
        }
        
        // Log batch summary instead of individual logs
        if (skipped.isNotEmpty() && log.isDebugEnabled) {
            log.debug("nodedoc: skipped {} items for {}: {}", skipped.size, label, skipped.take(5).joinToString())
        }
        
        val success = toStore.size
        if (success > 0 || skipped.isNotEmpty() || failed > 0) {
            log.info(
                "nodedoc: batch {} completed - success={}, skipped={}, failed={}",
                label,
                success,
                skipped.size,
                failed,
            )
        }
        
        return batch.size
    }

    private fun shouldAllowMissingDeps(node: Node): Boolean {
        if (node.kind != NodeKind.METHOD) return false
        val nodeId = node.id ?: return false
        val currentSkips = skipCounts[nodeId] ?: 0
        val maxSkips = maxSkipsFor(node)
        return currentSkips >= maxSkips
    }

    private fun maxSkipsFor(node: Node): Int {
        val appId = node.application.id ?: return skipMin
        val now = System.currentTimeMillis()
        
        val accessTime = appNodeCountsAccessTime.getOrPut(appId) { AtomicLong(now) }
        val cachedValue = appNodeCounts[appId]
        val age = now - accessTime.get()
        
        val totalNodes = if (cachedValue == null || age > cacheTtlMs) {
            // Refresh cache
            val fresh = nodeRepo.countByApplicationId(appId)
            appNodeCounts[appId] = fresh
            accessTime.set(now)
            fresh
        } else {
            // Use cached value and update access time
            accessTime.set(now)
            cachedValue
        }
        
        val byFactor = ceil(totalNodes * skipFactor).toInt()
        return max(skipMin, min(skipMax, byFactor))
    }
}

