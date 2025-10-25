package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.model.ChunkPlan
import com.bftcom.docgenerator.chunking.ChunkWriter
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.repo.ChunkRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.OffsetDateTime

@Service
class ChunkWriterImpl(
    private val chunkRepo: ChunkRepository
) : ChunkWriter {

    override fun savePlan(plans: List<ChunkPlan>): ChunkWriter.SaveResult {
        var w = 0L; var s = 0L
        for (p in plans) {
            val hash = sha256(p.content)
            val latest = chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(p.node.id!!)
            if (latest?.contentHash == hash) { s++; continue }

            val chunk = Chunk(
                id = null,
                application = p.node.application,
                node = p.node,
                source = p.source,
                kind = p.kind,
                langDetected = p.langDetected,
                contentRaw = null,
                content = p.content,
                contentTsv = null,
                contentHash = hash,
                tokenCount = p.content.split(Regex("\\s+")).count { it.isNotBlank() },
                chunkIndex = 0,
                spanLines = p.spanLines,   // @ColumnTransformer → ::int4range
                spanChars = null,
                title = p.title,
                sectionPath = p.sectionPath,
                usesMd = null,
                usedByMd = null,
                emb = null, embedModel = null, embedTs = null,
                explainMd = null,
                explainQuality = emptyMap(),
                relations = p.relations,
                usedObjects = p.usedObjects,
                pipeline = p.pipeline,
                freshnessAt = OffsetDateTime.now(),
                rankBoost = 1.0f,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now()
            )

            chunkRepo.save(chunk); w++
        }
        return ChunkWriter.SaveResult(written = w, skipped = s)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}