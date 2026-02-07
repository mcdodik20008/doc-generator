package com.bftcom.docgenerator.graph.impl.events

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.graph.api.events.GraphBuildRequestedEvent
import com.bftcom.docgenerator.graph.api.events.LibraryBuildRequestedEvent
import com.bftcom.docgenerator.library.api.LibraryBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Асинхронный обработчик событий сборки библиотек.
 * Парсит jar-файлы из classpath, строит граф библиотек и затем инициирует сборку графа приложения.
 */
@Component
class LibraryBuildEventListener(
    private val appRepo: ApplicationRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val libraryBuilder: LibraryBuilder,
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
            // Парсим jar-файлы и строим граф библиотек
            val result = libraryBuilder.buildLibraries(event.classpath)
            log.info(
                "Library build completed for app key={}: processed={}, skipped={}, nodes={}, errors={}",
                app.key,
                result.librariesProcessed,
                result.librariesSkipped,
                result.nodesCreated,
                result.errors.size,
            )

            if (result.errors.isNotEmpty()) {
                log.warn("Library build had {} errors for app key={}", result.errors.size, app.key)
            }

            // После завершения анализа библиотек инициируем сборку графа приложения
            val appId = requireNotNull(app.id) { "Application ID cannot be null" }
            eventPublisher.publishEvent(
                GraphBuildRequestedEvent(
                    applicationId = appId,
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
