package com.bftcom.docgenerator.chunking.scheduler

import com.bftcom.docgenerator.chunking.ai.chatclients.OllamaTalkerClient
import com.bftcom.docgenerator.chunking.factory.ExplainRequestFactory.toTalkerRewriteRequest
import com.bftcom.docgenerator.db.ChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime

@Service
class ContentFillerScheduler(
    txManager: PlatformTransactionManager,
    private val chunkRepo: ChunkRepository,
    private val talker: OllamaTalkerClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    @Value("\${docgen.fill.batch-size:10}")
    private var batchSize: Int = 10

    // @Scheduled(fixedDelayString = "\${docgen.fill.poll-ms:4000}")
    fun pollAndFill() {
        val batch = tx.execute { chunkRepo.lockNextBatchContentForFill(batchSize) } ?: return
        if (batch.isEmpty()) {
            return
        }

        for (chunk in batch) {
            try {
                val req = chunk.toTalkerRewriteRequest()
                log.info("Call req to model: {}", req.toString().take(150))
                val answer = talker.rewrite(req)
                tx.execute {
                    val reloaded = chunkRepo.findById(chunk.id!!).orElse(null) ?: return@execute
                    reloaded.content = answer
                    reloaded.updatedAt = OffsetDateTime.now()
                    chunkRepo.save(reloaded)
                }
                log.info("Filled content for chunk id={}", chunk.id)
            } catch (e: Exception) {
                log.error("Filling failed for chunk id=${chunk.id}: ${e.message}", e)
            }
        }
    }
}
