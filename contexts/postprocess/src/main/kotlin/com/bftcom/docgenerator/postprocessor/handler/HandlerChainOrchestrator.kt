package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import com.bftcom.docgenerator.postprocessor.utils.MutationMerger
import com.bftcom.docgenerator.postprocessor.utils.PpUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class HandlerChainOrchestrator(
    private val repo: ChunkRepository,
    private val handlers: List<PostprocessHandler>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processOne(chunk: Chunk) {
        log.debug("Processing chunk: id={}, nodeId={}, source={}", chunk.id, chunk.node.id, chunk.source)
        val snap = ChunkSnapshot.from(chunk)

        // собираем partial-mutations
        val patches =
            handlers
                .filter { it.supports(snap) }
                .mapNotNull { handler ->
                    try {
                        handler.produce(snap)
                    } catch (e: Throwable) {
                        log.warn(
                            "Handler failed for chunk: chunkId={}, handler={}, error={}",
                            snap.id,
                            handler::class.simpleName,
                            e.message,
                            e,
                        )
                        null
                    }
                }

        // начальное состояние заполняем текущими значениями из БД
        val initial =
            PartialMutation().apply {
                chunk.contentHash?.let { set(FieldKey.CONTENT_HASH, it) }
                chunk.tokenCount?.let { set(FieldKey.TOKEN_COUNT, it) }
                chunk.embedModel?.let { set(FieldKey.EMBED_MODEL, it) }
                chunk.embedTs?.let { set(FieldKey.EMBED_TS, it) }
                if (chunk.emb != null) set(FieldKey.EMB, chunk.emb!!)
            }

        val merged = MutationMerger.merge(initial, patches)

        // извлекаем итоговые значения
        val contentHash =
            (merged.provided[FieldKey.CONTENT_HASH] as? String) ?: snap.contentHash
                ?: PpUtil.sha256Hex(snap.content)
        val tokenCount =
            (merged.provided[FieldKey.TOKEN_COUNT] as? Int)
                ?: snap.tokenCount ?: Regex("""\S+""").findAll(snap.content).count()

        // 1) пишем базовую мету (без emb)
        try {
            repo.updateMeta(
                id = snap.id,
                contentHash = contentHash,
                tokenCount = tokenCount,
                embedModel = null,
                embedTs = null,
            )
            log.trace("Updated post metadata for chunk: id={}", snap.id)
        } catch (e: Exception) {
            log.error("Failed to update post metadata for chunk: id={}, error={}", snap.id, e.message, e)
            throw e
        }

        // 2) emb — отдельно (vector + embed_model/embed_ts)
        (merged.provided[FieldKey.EMB] as? FloatArray)?.let { vec ->
            val literal = "[" + vec.joinToString(",") { it.toString() } + "]"
            try {
                repo.updateEmb(snap.id, literal)
                log.trace("Updated embedding for chunk: id={}, vectorSize={}", snap.id, vec.size)
            } catch (e: Exception) {
                log.error("Failed to update embedding for chunk: id={}, error={}", snap.id, e.message, e)
                throw e
            }

            val model = merged.provided[FieldKey.EMBED_MODEL] as? String
            val ts = (merged.provided[FieldKey.EMBED_TS] as? OffsetDateTime) ?: OffsetDateTime.now()

            repo.updateMeta(
                id = snap.id,
                contentHash = contentHash,
                tokenCount = tokenCount,
                embedModel = model,
                embedTs = ts,
            )
        }
    }
}
