package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.db.SynonymDictionaryRepository
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Шаг расширения запроса (query expansion) с использованием synonym_dictionary.
 * 
 * Процесс:
 * 1. Генерирует эмбеддинг исходного запроса
 * 2. Ищет топ-3 кандидата по term_embedding
 * 3. Валидирует семантическое сходство: сравнивает эмбеддинг запроса с desc_embedding кандидатов
 * 4. Фильтрует по порогу косинусного сходства >0.7
 * 5. Формирует расширенный запрос: Original Query + (Контекст: [синонимы])
 * 6. Всегда переходит к VECTOR_SEARCH
 */
@Component
class ExpansionStep(
    private val synonymRepo: SynonymDictionaryRepository,
    private val embeddingClient: EmbeddingClient,
    @param:Value("\${docgen.rag.expansion.top-k:3}")
    private val topK: Int = 3,
    @param:Value("\${docgen.rag.expansion.similarity-threshold:0.7}")
    private val similarityThreshold: Double = 0.7,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.EXPANSION

    override fun execute(context: QueryProcessingContext): StepResult {
        val currentQuery = context.currentQuery

        // Пропускаем расширение, если уже было выполнено
        if (context.hasMetadata(QueryMetadataKeys.EXPANDED)) {
            log.debug("Запрос уже расширен, пропускаем шаг EXPANSION")
            return StepResult(
                context = context,
                transitionKey = "SUCCESS",
            )
        }

        try {
            // 1. Генерируем эмбеддинг исходного запроса
            val queryEmbedding = embeddingClient.embed(currentQuery)
            val queryEmbeddingStr = queryEmbedding.joinToString(",", "[", "]") { it.toString() }

            // 2. Ищем топ-3 кандидата по term_embedding
            val candidates = synonymRepo.findTopByTermEmbedding(
                queryEmbedding = queryEmbeddingStr,
                topK = topK,
            )

            if (candidates.isEmpty()) {
                log.debug("ExpansionStep: не найдено кандидатов для запроса '{}'", currentQuery)
                return StepResult(
                    context = context.setMetadata(QueryMetadataKeys.EXPANDED, true),
                    transitionKey = "SUCCESS",
                )
            }

            // 3. Валидируем семантическое сходство: фильтруем по desc_embedding с порогом >0.7
            val validated = synonymRepo.findByDescEmbeddingWithThreshold(
                queryEmbedding = queryEmbeddingStr,
                threshold = similarityThreshold,
            )

            // Берем только те, что есть и в топ-3 по term_embedding, и в отфильтрованных по desc_embedding
            val candidateIds = candidates.map { it.getId() }.toSet()
            val validatedSynonyms = validated
                .filter { it.getId() in candidateIds }
                .take(topK)

            if (validatedSynonyms.isEmpty()) {
                log.debug(
                    "ExpansionStep: не найдено валидных синонимов (порог={}) для запроса '{}'",
                    similarityThreshold,
                    currentQuery
                )
                return StepResult(
                    context = context.setMetadata(QueryMetadataKeys.EXPANDED, true),
                    transitionKey = "SUCCESS",
                )
            }

            // 4. Формируем расширенный запрос: Original Query + (Контекст: [синонимы])
            val synonymContext = validatedSynonyms
                .map { "${it.getTerm()}: ${it.getDescription()}" }
                .joinToString("; ")

            val expandedQuery = if (synonymContext.isNotBlank()) {
                "$currentQuery (Контекст: [$synonymContext])"
            } else {
                currentQuery
            }

            val synonymsInfo = validatedSynonyms.map { mapOf(
                "term" to it.getTerm(),
                "description" to it.getDescription(),
                "nodeId" to it.getSourceNodeId(),
            ) }

            // 5. Обновляем контекст
            val updatedContext = context
                .updateQuery(expandedQuery)
                .setMetadata(QueryMetadataKeys.EXPANDED, true)
                .setMetadata(QueryMetadataKeys.EXPANDED_SYNONYMS, synonymsInfo)
                .addStep(
                    ProcessingStep(
                        advisorName = "ExpansionStep",
                        input = currentQuery,
                        output = "Найдено ${validatedSynonyms.size} релевантных синонимов. Расширенный запрос: $expandedQuery",
                        stepType = type,
                        status = ProcessingStepStatus.SUCCESS,
                    ),
                )

            log.debug(
                "ExpansionStep: расширен запрос '{}' с {} синонимами",
                currentQuery,
                validatedSynonyms.size
            )

            return StepResult(
                context = updatedContext,
                transitionKey = "SUCCESS",
            )
        } catch (e: Exception) {
            log.warn("ExpansionStep: ошибка при расширении запроса '{}': {}", currentQuery, e.message, e)
            // При ошибке продолжаем с оригинальным запросом
            return StepResult(
                context = context
                    .setMetadata(QueryMetadataKeys.EXPANDED, true)
                    .addStep(
                        ProcessingStep(
                            advisorName = "ExpansionStep",
                            input = currentQuery,
                            output = "Ошибка расширения: ${e.message}. Используется оригинальный запрос",
                            stepType = type,
                            status = ProcessingStepStatus.SUCCESS,
                        ),
                    ),
                transitionKey = "SUCCESS",
            )
        }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.VECTOR_SEARCH,
        )
    }
}
