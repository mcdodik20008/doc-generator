package com.bftcom.docgenerator.graph.impl.events

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.graph.api.events.LinkRequestedEvent
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Асинхронный обработчик события линковки графа.
 * Запускает GraphLinker.link в фоне для построения edges между нодами.
 */
@Component
class LinkEventListener(
    private val graphLinker: GraphLinker,
    private val appRepo: ApplicationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onLinkRequested(event: LinkRequestedEvent) {
        val app =
            appRepo.findById(event.applicationId).orElse(null)
                ?: run {
                    log.warn("LinkRequestedEvent: application id={} not found", event.applicationId)
                    return
                }

        log.info("Async link started for application id={} key={}", app.id, app.key)

        try {
            graphLinker.link(application = app)

            log.info("Async link done for app key={}", app.key)
        } catch (e: Exception) {
            log.error("Async link failed for app key={}: {}", app.key, e.message, e)
            // Ошибка линковки не должна падать на весь процесс ingest,
            // но мы явно её логируем.
        }
    }
}

