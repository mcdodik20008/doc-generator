package com.bftcom.docgenerator.chunking.scheduler

import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.chunking.model.toCoderExplainRequest
import com.bftcom.docgenerator.chunking.model.toTalkerRewriteRequest
import com.bftcom.docgenerator.repo.ChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime

@Service
class RawContentFillerScheduler(
    txManager: PlatformTransactionManager,
    private val chunkRepo: ChunkRepository,
    private val coder: OllamaCoderClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    @Value("\${docgen.fill.batch-size:10}")
    private var batchSize: Int = 10

    // @Scheduled(fixedDelayString = "\${docgen.fill.poll-ms:4000}")
    fun pollAndFill() {
        val batch = tx.execute { chunkRepo.lockNextBatchForRawFill(batchSize) } ?: return
        if (batch.isEmpty()) {
            log.warn("Chunks batch is empty")
            return
        }

        for (chunk in batch) {
            try {
                val req = chunk.toCoderExplainRequest()
                if (req.codeExcerpt.isBlank()) {
                    log.warn("Chunk id={} has empty prepared code — skip", chunk.id)
                    continue
                }

                val answer = coder.explain(req)
                tx.execute {
                    val reloaded = chunkRepo.findById(chunk.id!!).orElse(null) ?: return@execute
                    if (!reloaded.contentRaw.isNullOrBlank()) {
                        return@execute
                    }

                    reloaded.contentRaw = answer
                    reloaded.updatedAt = OffsetDateTime.now()
                    chunkRepo.save(reloaded)
                }

                log.info("Filled content_raw for chunk id={}", chunk.id)
            } catch (e: Exception) {
                log.error("Filling failed for chunk id=${chunk.id}: ${e.message}", e)
            }
        }
    }
}
