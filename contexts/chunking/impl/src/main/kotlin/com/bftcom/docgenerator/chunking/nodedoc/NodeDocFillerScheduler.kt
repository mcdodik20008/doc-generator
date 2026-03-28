package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.node.Node
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

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

    @PostConstruct
    fun validateConfiguration() {
        require(batchSize in 1..10000) {
            "docgen.nodedoc.batch-size must be between 1 and 10000, but was $batchSize"
        }
        log.info("NodeDocFillerScheduler initialized with batchSize=$batchSize, locale=$locale")
    }

    @Scheduled(fixedDelayString = "\${docgen.nodedoc.poll-ms:5000}")
    fun poll() {
        log.info("nodedoc: NodeDocFillerScheduler")
        // Шаг 1: находим узлы, у которых все зависимости уже задокументированы (bottom-up)
        val batch = tx.execute { nodeRepo.lockNextReadyNodesWithoutDoc(locale, batchSize) } ?: emptyList()
        if (batch.isNotEmpty()) {
            processBatch("READY", batch)
            return
        }
        // Шаг 2: фоллбэк — разрываем циклы, берём любой незадокументированный узел
        val cycleBatch = tx.execute { nodeRepo.lockNextAnyNodesWithoutDoc(locale, batchSize) } ?: emptyList()
        if (cycleBatch.isNotEmpty()) {
            log.info("nodedoc: breaking dependency cycle, forcing {} nodes", cycleBatch.size)
            processBatch("CYCLE-BREAK", cycleBatch)
        }
    }

    internal fun processBatch(
        label: String,
        batch: List<Node>,
    ) {
        var success = 0
        var failed = 0

        for (n in batch) {
            val nodeId = n.id ?: continue
            try {
                val generated = generator.generate(n, locale)
                tx.execute { generator.store(nodeId, locale, generated) }
                success++
            } catch (t: Throwable) {
                failed++
                log.warn("nodedoc: failed nodeId={} fqn={}: {}", nodeId, n.fqn, t.message, t)
            }
        }
        if (success > 0 || failed > 0) {
            log.info("nodedoc: batch {} — success={}, failed={}", label, success, failed)
        }
    }
}
