package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.NodeDocRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class ChunkFromNodeDocScheduler(
    txManager: PlatformTransactionManager,
    private val nodeDocRepo: NodeDocRepository,
    private val chunkRepo: ChunkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(txManager)

    @Value("\${docgen.rag.chunk-sync.batch-size:50}")
    private var batchSize: Int = 50

    @Scheduled(fixedDelayString = "\${docgen.rag.chunk-sync.poll-ms:4000}")
    fun poll() {
        val rows = tx.execute { nodeDocRepo.lockNextBatchForChunkSync(batchSize) } ?: return
        if (rows.isEmpty()) return

        var written = 0
        for (r in rows) {
            val metaBase =
                mapOf(
                    "node_id" to r.getNodeId(),
                    "locale" to r.getLocale(),
                )
            val appId = r.getApplicationId()
            val nodeId = r.getNodeId()
            val locale = r.getLocale()

            r.getDocPublic()?.takeIf { it.isNotBlank() }?.let { content ->
                val meta = metaBase + mapOf("doc_variant" to "public")
                tx.execute {
                    chunkRepo.upsertDocChunk(
                        applicationId = appId,
                        nodeId = nodeId,
                        locale = locale,
                        kind = "public",
                        content = content,
                        metadataJson = JsonLite.stringify(meta),
                    )
                }
                written++
            }
            r.getDocTech()?.takeIf { it.isNotBlank() }?.let { content ->
                val meta = metaBase + mapOf("doc_variant" to "tech")
                tx.execute {
                    chunkRepo.upsertDocChunk(
                        applicationId = appId,
                        nodeId = nodeId,
                        locale = locale,
                        kind = "tech",
                        content = content,
                        metadataJson = JsonLite.stringify(meta),
                    )
                }
                written++
            }
        }

        log.info("chunk-sync: processed {} node_doc rows, upserts={}", rows.size, written)
    }
}

