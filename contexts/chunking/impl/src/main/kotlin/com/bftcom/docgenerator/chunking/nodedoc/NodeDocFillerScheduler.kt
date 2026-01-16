package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.node.Node
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

    @Scheduled(fixedDelayString = "\${docgen.nodedoc.poll-ms:5000}")
    fun poll() {
        // bottom-up order
        processBatch("METHOD") { nodeRepo.lockNextMethodsWithoutDoc(locale, batchSize) }
        processBatch("TYPE") { nodeRepo.lockNextTypesWithoutDoc(locale, batchSize) }
        processBatch("PACKAGE") { nodeRepo.lockNextPackagesWithoutDoc(locale, batchSize) }
        processBatch("MODULE/REPO") { nodeRepo.lockNextModulesAndReposWithoutDoc(locale, batchSize) }
    }

    private fun processBatch(label: String, loader: () -> List<Node>) {
        val batch = tx.execute { loader() } ?: return
        if (batch.isEmpty()) return
        log.info("nodedoc: generating {} items for {}", batch.size, label)
        for (n in batch) {
            val nodeId = n.id ?: continue
            try {
                val generated = generator.generate(n, locale)
                tx.execute {
                    generator.store(nodeId, locale, generated)
                }
            } catch (t: Throwable) {
                log.warn("nodedoc: failed for nodeId={} fqn={}: {}", nodeId, n.fqn, t.message, t)
            }
        }
    }
}

