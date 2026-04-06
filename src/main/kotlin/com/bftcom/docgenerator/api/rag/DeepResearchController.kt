package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.rag.api.DeepResearchResponse
import com.bftcom.docgenerator.rag.api.DeepResearchService
import com.bftcom.docgenerator.rag.api.ResearchEvent
import com.bftcom.docgenerator.rag.api.ResearchEventType
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/rag/research")
class DeepResearchController(
    private val deepResearchService: DeepResearchService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun research(
        @RequestBody @Valid request: RagRequest,
    ): DeepResearchResponse {
        log.info("Deep research sync request: sessionId={}, query_length={}", request.sessionId, request.query.length)
        return deepResearchService.research(request.query, request.sessionId, request.applicationId)
    }

    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun researchStream(
        @RequestBody @Valid request: RagRequest,
    ): Flux<ServerSentEvent<String>> {
        log.info("Deep research stream request: sessionId={}, query_length={}", request.sessionId, request.query.length)

        val eventSink = reactor.core.publisher.Sinks
            .many()
            .multicast()
            .onBackpressureBuffer<ServerSentEvent<String>>()

        val researchMono = Mono
            .fromCallable {
                deepResearchService.researchWithProgress(
                    query = request.query,
                    sessionId = request.sessionId,
                    applicationId = request.applicationId,
                ) { event: ResearchEvent ->
                    val sse = buildSseEvent(event)
                    val result = eventSink.tryEmitNext(sse)
                    if (result.isFailure) {
                        log.warn("Failed to emit SSE event: {}", result)
                    }
                }
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { response ->
                // Emit sources
                val sourcesEvent = ServerSentEvent.builder<String>()
                    .event("sources")
                    .data(objectMapper.writeValueAsString(response.sources))
                    .build()
                eventSink.tryEmitNext(sourcesEvent)

                // Emit done
                val doneEvent = ServerSentEvent.builder<String>()
                    .event("done")
                    .data(objectMapper.writeValueAsString(mapOf(
                        "iterationsUsed" to response.iterationsUsed,
                        "totalDurationMs" to response.totalDurationMs,
                    )))
                    .build()
                eventSink.tryEmitNext(doneEvent)
                eventSink.tryEmitComplete()
            }
            .doOnError { e ->
                log.error("Deep research stream failed: {}", e.message, e)
                val errorEvent = ServerSentEvent.builder<String>()
                    .event("error")
                    .data(objectMapper.writeValueAsString(mapOf("message" to (e.message ?: "Unknown error"))))
                    .build()
                eventSink.tryEmitNext(errorEvent)
                eventSink.tryEmitComplete()
            }
            .subscribe()

        return eventSink.asFlux()
            .doFinally { researchMono.dispose() }
    }

    private fun buildSseEvent(event: ResearchEvent): ServerSentEvent<String> {
        val eventType = when (event.type) {
            ResearchEventType.THINKING -> "thinking"
            ResearchEventType.ACTION -> "action"
            ResearchEventType.OBSERVATION -> "observation"
            ResearchEventType.ANSWER -> "answer"
            ResearchEventType.ERROR -> "error"
        }
        val data = objectMapper.writeValueAsString(mapOf(
            "iteration" to event.iteration,
            "content" to event.content,
            "timestamp" to event.timestamp,
        ))
        return ServerSentEvent.builder<String>()
            .event(eventType)
            .data(data)
            .build()
    }
}
