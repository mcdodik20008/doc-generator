package com.bftcom.docgenerator.postprocess.scheduller

import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.postprocess.handler.HandlerChainOrchestrator
import com.bftcom.docgenerator.repo.ChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class ChunkPostprocessScheduler(
    txManager: PlatformTransactionManager,
    private val repo: ChunkRepository,
    private val orchestrator: HandlerChainOrchestrator,
    @Value("\${docgen.post.batch-size:50}")
    private val batchSize: Int,
    @Value("\${docgen.embed.enabled:true}")
    private val embedEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    fun lockBatch(): List<Chunk> =
        repo.lockNextBatchForPostprocess(
            limit = batchSize,
            withEmb = embedEnabled,
        )

    // @Scheduled(fixedDelayString = "\${docgen.post.poll-ms:5000}")
    fun poll() {
        val batch = tx.execute { lockBatch() } ?: throw RuntimeException("Не получилось вычитать из бдхи")
        if (batch.isEmpty()) {
            return
        }
        log.info("Postprocess chain: {} chunks", batch.size)

        batch.forEach { ch ->
            try {
                orchestrator.processOne(ch)
            } catch (t: Throwable) {
                log.warn("Chain failed for chunk id={}", ch.id, t)
            }
        }
    }
}
