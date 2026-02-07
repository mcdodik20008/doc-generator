package com.bftcom.docgenerator.graph.impl.events

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.graph.api.events.GraphBuildRequestedEvent
import com.bftcom.docgenerator.graph.api.events.LinkRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * Асинхронный обработчик события сборки графа.
 * Запускает GraphBuilder.build в фоне, обновляя статус Application.
 */
@Component
class GraphBuildEventListener(
    private val graphBuilder: GraphBuilder,
    private val appRepo: ApplicationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onGraphBuildRequested(event: GraphBuildRequestedEvent) {
        val app =
            appRepo.findById(event.applicationId).orElse(null)
                ?: run {
                    log.warn("GraphBuildRequestedEvent: application id={} not found", event.applicationId)
                    return
                }

        log.info("Async graph build started for application id={} key={}", app.id, app.key)

        try {
            app.lastIndexStatus = "running"
            app.lastIndexedAt = OffsetDateTime.now()
            app.lastIndexError = null
            appRepo.save(app)

            val result =
                graphBuilder.build(
                    application = app,
                    sourceRoot = event.sourceRoot,
                    classpath = event.classpath,
                )

            app.lastIndexStatus = "success"
            app.lastIndexedAt = result.finishedAt
            app.lastIndexError = null
            appRepo.save(app)

            log.info(
                "Async graph build done for app key={}: nodes={}, edges={}",
                app.key,
                result.nodes,
                result.edges,
            )

            // После успешной сборки графа инициируем линковку
            val appId = requireNotNull(app.id) { "Application ID cannot be null" }
            eventPublisher.publishEvent(
                LinkRequestedEvent(applicationId = appId),
            )
            log.debug("LinkRequestedEvent published for application key={}", app.key)
        } catch (e: Exception) {
            app.lastIndexStatus = "failed"
            app.lastIndexedAt = OffsetDateTime.now()
            app.lastIndexError = e.message ?: e::class.java.simpleName
            appRepo.save(app)

            log.error("Async graph build failed for app key={}: {}", app.key, e.message, e)
        }
    }
}
