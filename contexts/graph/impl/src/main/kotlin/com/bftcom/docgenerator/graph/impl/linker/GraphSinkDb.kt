package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.graph.api.linker.detector.EdgeProposal
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GraphSinkDb(
    private val edgeRepository: EdgeRepository,
) : GraphSink {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Пишем рёбра 1:1 через текущий репозиторий.
     * Никакой новой логики: если у ноды нет id — пропускаем.
     */
    @Transactional
    override fun upsertEdges(edges: Sequence<EdgeProposal>) {
        var written = 0
        edges.forEach { e ->
            val srcId = e.source.id
            val dstId = e.target.id
            if (srcId != null && dstId != null) {
                edgeRepository.upsert(srcId, dstId, e.kind.name)
                written++
            } else {
                // тихо пропускаем «сырые» ноды без id
                // (политика полностью соответствует прежнему upsertEdge(..) в линкере)
            }
        }
        log.info("GraphSinkDb: upserted $written edges")
    }
}
