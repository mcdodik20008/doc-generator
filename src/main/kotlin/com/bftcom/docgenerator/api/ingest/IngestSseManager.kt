package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestEventDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

@Component
class IngestSseManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sinks = ConcurrentHashMap<Long, Sinks.Many<IngestEventDto>>()

    fun emit(
        runId: Long,
        event: IngestEventDto,
    ) {
        val sink =
            sinks.computeIfAbsent(runId) {
                Sinks.many().multicast().onBackpressureBuffer()
            }
        sink.tryEmitNext(event)
    }

    fun stream(runId: Long): Flux<IngestEventDto> {
        val sink =
            sinks.computeIfAbsent(runId) {
                Sinks.many().multicast().onBackpressureBuffer()
            }
        return sink.asFlux()
    }

    fun complete(runId: Long) {
        val sink = sinks.remove(runId)
        if (sink != null) {
            sink.tryEmitComplete()
            log.debug("SSE sink completed and removed for runId={}", runId)
        }
    }
}
