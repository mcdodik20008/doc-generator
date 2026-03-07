package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestEventDto
import com.bftcom.docgenerator.api.ingest.dto.IngestRunDto
import com.bftcom.docgenerator.api.ingest.dto.IngestStartRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/ingest")
class IngestRunController(
    private val orchestrator: IngestRunOrchestrator,
    private val sseManager: IngestSseManager,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/start/{appId}")
    fun start(
        @PathVariable appId: Long,
        @RequestBody(required = false) request: IngestStartRequest?,
    ): IngestRunDto {
        log.info("Ingest start requested: appId=$appId, branch=${request?.branch}, triggeredBy=${request?.triggeredBy}")
        return orchestrator.startRun(appId, request?.branch, request?.triggeredBy ?: "api")
    }

    @GetMapping("/runs/{runId}")
    fun getRunStatus(@PathVariable runId: Long): IngestRunDto {
        return orchestrator.getRunStatus(runId)
    }

    @GetMapping("/runs/{runId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(@PathVariable runId: Long): Flux<ServerSentEvent<String>> {
        log.info("SSE stream requested for runId=$runId")

        return Mono.fromCallable {
            orchestrator.getEvents(runId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { pastEvents ->
                // Past events from DB
                val pastFlux = Flux.fromIterable(pastEvents)
                    .map { event -> toSse(event) }

                // Live events from sink
                val liveFlux = sseManager.stream(runId)
                    .map { event -> toSse(event) }

                Flux.concat(pastFlux, liveFlux)
            }
            .onErrorResume { e ->
                log.error("SSE stream error for runId=$runId: ${e.message}", e)
                val errorEvent = ServerSentEvent.builder<String>()
                    .event("error")
                    .data(e.message ?: "An error occurred")
                    .build()
                Flux.just(errorEvent)
            }
    }

    @GetMapping("/runs/app/{appId}")
    fun getRunsByApp(@PathVariable appId: Long): List<IngestRunDto> {
        return orchestrator.getRunsByApp(appId)
    }

    private fun toSse(event: IngestEventDto): ServerSentEvent<String> {
        val eventType = when {
            event.level == "ERROR" -> "error"
            event.message.contains("started") -> "step_started"
            event.message.contains("completed") || event.message.contains("built") -> "step_completed"
            event.message.contains("failed") -> "step_failed"
            event.message.startsWith("Ingest run completed") -> "run_completed"
            else -> "log"
        }
        return ServerSentEvent.builder<String>()
            .event(eventType)
            .data(objectMapper.writeValueAsString(event))
            .build()
    }
}
