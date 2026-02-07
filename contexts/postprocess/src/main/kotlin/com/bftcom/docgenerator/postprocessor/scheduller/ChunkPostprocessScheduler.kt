package com.bftcom.docgenerator.postprocessor.scheduller

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.postprocessor.handler.HandlerChainOrchestrator
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
    @param:Value("\${docgen.post.batch-size:50}")
    private val batchSize: Int,
    @param:Value("\${docgen.embed.enabled:true}")
    private val embedEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    init {
        require(batchSize in 1..10000) {
            "docgen.post.batch-size must be between 1 and 10000, but was $batchSize"
        }
        log.info("ChunkPostprocessScheduler initialized with batchSize=$batchSize, embedEnabled=$embedEnabled")
    }

    fun lockBatch(): List<Chunk> =
        repo.lockNextBatchForPostprocess(
            limit = batchSize,
            withEmb = embedEnabled,
        )

    @Scheduled(fixedDelayString = "\${docgen.post.poll-ms:5000}")
    fun poll() {
        try {
            val batch = tx.execute { lockBatch() }
                ?: throw IllegalStateException("Failed to acquire batch lock from database - transaction returned null")

            if (batch.isEmpty()) {
                return
            }

            log.info("Postprocess chain: processing {} chunks", batch.size)

            var successCount = 0
            var errorCount = 0
            val failedChunks = mutableListOf<Pair<Long, String>>() // Сохраняем информацию об ошибках

            batch.forEach { ch ->
                try {
                    orchestrator.processOne(ch)
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                    val errorType = e.javaClass.simpleName
                    val errorMsg = e.message ?: "Unknown error"

                    // Сохраняем информацию для метрик и потенциального retry
                    ch.id?.let {
                        failedChunks.add(it to errorType)
                    }

                    // Логируем с полным контекстом для отладки
                    log.error(
                        "Postprocess failed: chunkId={}, errorType={}, message={}, applicationId={}, nodeId={}",
                        ch.id,
                        errorType,
                        errorMsg,
                        ch.application?.id,
                        ch.node?.id,
                        e
                    )
                }
            }

            if (failedChunks.isNotEmpty()) {
                log.warn(
                    "Postprocess batch completed with errors: success={}, errors={}, total={}, failedChunkIds={}",
                    successCount,
                    errorCount,
                    batch.size,
                    failedChunks.take(10).map { it.first } // Показываем первые 10 для краткости
                )
            } else {
                log.info("Postprocess batch completed successfully: success={}, total={}", successCount, batch.size)
            }

        } catch (e: Exception) {
            log.error("Critical error in postprocess scheduler poll cycle", e)
            // Не пробрасываем исключение, чтобы scheduler продолжил работу
        }
    }
}
