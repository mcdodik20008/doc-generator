package com.bftcom.docgenerator.graph.impl.events

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.graph.api.events.GraphBuildRequestedEvent
import com.bftcom.docgenerator.graph.api.events.LibraryBuildRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Асинхронный обработчик событий сборки библиотек.
 * Пока что реализует только "каркас": логирует и сразу инициирует сборку графа приложения.
 * В будущем сюда будет встроен полноценный анализ jar/classpath с заполнением Library/LibraryNode.
 */
@Component
class LibraryBuildEventListener(
    private val appRepo: ApplicationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun onLibraryBuildRequested(event: LibraryBuildRequestedEvent) {
        val app =
            appRepo.findById(event.applicationId).orElse(null)
                ?: run {
                    log.warn("LibraryBuildRequestedEvent: application id={} not found", event.applicationId)
                    return
                }

        log.info(
            "Async library build started for application id={} key={} (classpath entries={})",
            app.id,
            app.key,
            event.classpath.size,
        )

        try {
            // TODO: здесь должен быть реальный анализ classpath/jar и заполнение Library/LibraryNode
            // Сейчас просто логируем как заглушку.
            log.debug(
                "Library build stub: skipping real jar analysis for app key={} (will be implemented later)",
                app.key,
            )

            // После завершения анализа библиотек инициируем сборку графа приложения
            eventPublisher.publishEvent(
                GraphBuildRequestedEvent(
                    applicationId = app.id!!,
                    sourceRoot = event.sourceRoot,
                    classpath = event.classpath,
                ),
            )

            log.info(
                "Async library build finished for application key={}, graph build event published",
                app.key,
            )
        } catch (e: Exception) {
            log.error("Library build failed for application key={}: {}", app.key, e.message, e)
            // Ошибка анализа библиотек не должна падать на весь процесс ingest,
            // но мы явно её логируем.
        }
    }
}


