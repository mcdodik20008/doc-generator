package com.bftcom.docgenerator.api.ingest

import com.bftcom.docgenerator.api.ingest.dto.IngestRunRequest
import com.bftcom.docgenerator.git.api.GitIngestOrchestratorFactory
import com.bftcom.docgenerator.git.model.IngestSummary
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ingest")
class IngestController(
    private val orchestratorFactory: GitIngestOrchestratorFactory,
) {
    @PostMapping("/run")
    // TODO: Метод может выполняться очень долго (клонирование репозитория, парсинг, построение графа)
    // TODO: Использовать асинхронную обработку (@Async) или возвращать task ID для отслеживания прогресса
    // TODO: Нет timeout - запрос может висеть бесконечно долго
    fun run(
        @RequestBody @Valid req: IngestRunRequest,
    ): IngestSummary {
        // TODO: Отсутствует логирование начала операции ingestion
        // TODO: Нет обработки ошибок - если операция упадет, вернется generic 500
        // Определяем провайдера из repoPath (может быть полный URL или путь)
        val repoPath = req.repoPath()
        // TODO: Простая проверка на http:// и https:// не покрывает все случаи (git://, ssh://)
        // TODO: Нет валидации URL формата (может быть некорректный URL)
        val repoUrl = if (repoPath.startsWith("http://") || repoPath.startsWith("https://")) {
            repoPath
        } else {
            null // Будет определен позже в orchestrator
        }

        val orchestrator = orchestratorFactory.getOrchestrator(repoUrl)
        // TODO: Hardcoded значение "develop" для ветки - должно быть в конфигурации
        // TODO: Нет проверки что orchestrator не null
        val summary: IngestSummary =
            orchestrator.runOnce(
                appKey = req.appKey,
                repoPath = repoPath,
                branch = req.branch ?: "develop",
            )
        return summary
    }
}
