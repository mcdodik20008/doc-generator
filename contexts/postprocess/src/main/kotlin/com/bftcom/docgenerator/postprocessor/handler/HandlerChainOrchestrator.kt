package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import com.bftcom.docgenerator.postprocessor.utils.MutationMerger
import com.bftcom.docgenerator.postprocessor.utils.PpUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class HandlerChainOrchestrator(
    private val repo: ChunkRepository,
    private val handlers: List<PostprocessHandler>,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processOne(chunk: Chunk) {
        val snap = ChunkSnapshot.from(chunk)

        // собираем partial-mutations
        val patches =
            handlers
                .filter { it.supports(snap) }
                .mapNotNull {
                    try {
                        it.produce(snap)
                    } catch (_: Throwable) {
                        null
                    }
                }

        // начальное состояние заполняем текущими значениями из БД
        val initial =
            PartialMutation().apply {
                chunk.contentHash?.let { set(FieldKey.CONTENT_HASH, it) }
                chunk.tokenCount?.let { set(FieldKey.TOKEN_COUNT, it) }
                chunk.spanChars?.let { set(FieldKey.SPAN_CHARS, it) }
                chunk.usesMd?.let { set(FieldKey.USES_MD, it) }
                chunk.usedByMd?.let { set(FieldKey.USED_BY_MD, it) }
                chunk.explainMd?.let { set(FieldKey.EXPLAIN_MD, it) }
                if (chunk.explainQuality.isNotEmpty()) {
                    set(FieldKey.EXPLAIN_QUALITY_JSON, chunk.explainQuality.toString())
                }
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
        val spanChars =
            (merged.provided[FieldKey.SPAN_CHARS] as? String)
                ?: snap.spanChars ?: "[0,${snap.content.length})"
        val usesMd = merged.provided[FieldKey.USES_MD] as? String ?: snap.usesMd
        val usedByMd = merged.provided[FieldKey.USED_BY_MD] as? String ?: snap.usedByMd
        val explainMd = merged.provided[FieldKey.EXPLAIN_MD] as? String ?: snap.explainMd ?: ""
        val explainQualityJson =
            (merged.provided[FieldKey.EXPLAIN_QUALITY_JSON] as? String)
                ?: snap.explainQualityJson ?: """{}"""

        // 1) пишем всё, кроме emb
        repo.updatePostMeta(
            id = snap.id,
            contentHash = contentHash,
            tokenCount = tokenCount,
            spanChars = spanChars,
            usesMd = usesMd,
            usedByMd = usedByMd,
            embedModel = null,
            embedTs = null,
            explainMd = explainMd,
            explainQualityJson = explainQualityJson,
        )

        // 2) emb — отдельно
        (merged.provided[FieldKey.EMB] as? FloatArray)?.let { vec ->
            val literal = "[" + vec.joinToString(",") { it.toString() } + "]"
            repo.updateEmb(snap.id, literal)

            val model = merged.provided[FieldKey.EMBED_MODEL] as? String
            val ts = (merged.provided[FieldKey.EMBED_TS] as? OffsetDateTime) ?: OffsetDateTime.now()

            repo.updatePostMeta(
                id = snap.id,
                contentHash = contentHash,
                tokenCount = tokenCount,
                spanChars = spanChars,
                usesMd = usesMd,
                usedByMd = usedByMd,
                embedModel = model,
                embedTs = ts,
                explainMd = explainMd,
                explainQualityJson = explainQualityJson,
            )
        }
    }
}
