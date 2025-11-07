package com.bftcom.docgenerator.chunking.scheduler

import com.bftcom.docgenerator.ai.chatclients.OllamaCoderClient
import com.bftcom.docgenerator.chunking.factory.ExplainRequestFactory.toCoderExplainRequest
import com.bftcom.docgenerator.chunking.guards.LangGuards
import com.bftcom.docgenerator.db.ChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
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

    @Value("\${docgen.fill.lang.min-cyr-ratio:0.6}")
    private var minCyrRatio: Double = 0.6

    @Value("\${docgen.fill.lang.max-regens:2}")
    private var maxRegens: Int = 2

    @Scheduled(fixedDelayString = "\${docgen.fill.poll-ms:4000}")
    fun pollAndFill() {
        val batch = tx.execute { chunkRepo.lockNextBatchForRawFill(batchSize) } ?: return
        if (batch.isEmpty()) {
            log.debug("raw-fill: no chunks")
            return
        }

        for (chunk in batch) {
            try {
                val req = chunk.toCoderExplainRequest()
                if (req.codeExcerpt.isBlank()) {
                    log.warn("raw-fill: chunk id={} has no code — skip", chunk.id)
                    continue
                }

                log.info("Process chunk with id: ${chunk.id} ask $req.")
                var answer = coder.explain(req)
                var regen = 0

                // ЯЗЫКОВОЙ ФИЛЬТР → перегенерация при необходимости
                while (!LangGuards.isRussianEnough(answer, minCyrRatio) && regen < maxRegens) {
                    regen++
                    log.info(
                        "raw-fill: non-RU answer (ratio={}), regen {}/{} for chunk id={}",
                        "%.2f".format(LangGuards.cyrillicRatio(answer)),
                        regen,
                        maxRegens,
                        chunk.id,
                    )
                    val reinforcedReq =
                        req.copy(
                            // усиливаем подсказки для модели, сохраняя совместимость
                            hints =
                                (
                                    (
                                        req.hints
                                            ?: ""
                                    ) + "\nТребование: Ответ ДОЛЖЕН быть на РУССКОМ языке. Используй кириллицу."
                                ).trim(),
                        )
                    // небольшая пауза чтобы не лупить моментально
                    Thread.sleep((500L * regen).coerceAtMost(1500L))
                    answer = coder.explain(reinforcedReq)
                }

                val sha = sha256(answer)
                val updated =
                    tx.execute {
                        chunkRepo.trySetRawContent(
                            id = chunk.id!!,
                            content = answer,
                            updatedAt = OffsetDateTime.now(),
                        )
                    } ?: 0

                if (updated > 0) {
                    val ratio = LangGuards.cyrillicRatio(answer)
                    log.info(
                        "raw-fill: filled content_raw id={} (sha={}, cyrRatio={}){}",
                        chunk.id,
                        sha.take(8),
                        "%.2f".format(ratio),
                        if (regen > 0) " after $regen regen(s)" else "",
                    )
                } else {
                    log.info("raw-fill: skip write — already filled by another worker (id={})", chunk.id)
                }
            } catch (e: Exception) {
                log.error("raw-fill: failed for chunk id=${chunk.id}: ${e.message}", e)
            }
        }
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
