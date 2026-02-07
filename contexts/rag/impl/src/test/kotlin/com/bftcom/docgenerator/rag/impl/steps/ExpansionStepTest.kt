package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.db.SynonymDictionaryRepository
import com.bftcom.docgenerator.rag.api.*
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ExpansionStepTest {

    private val synonymRepo = mockk<SynonymDictionaryRepository>()
    private val embeddingClient = mockk<EmbeddingClient>()

    private val topK = 3
    private val threshold = 0.7

    private val step = ExpansionStep(
        synonymRepo = synonymRepo,
        embeddingClient = embeddingClient,
        topK = topK,
        similarityThreshold = threshold
    )

    private val query = "Как настроить авторизацию?"
    private val mockVector = floatArrayOf(0.1f, 0.2f)
    private val vectorStr = "[0.1,0.2]"

    @BeforeEach
    fun setUp() {
        every { embeddingClient.embed(any()) } returns mockVector
    }

    @Test
    @DisplayName("Успешное расширение: синоним прошел обе проверки (term и desc)")
    fun `execute - success expansion when synonym passes both checks`() {
        // Arrange
        val synonym = createMockSynonym(1L, "Auth", "Механизм входа")

        every { synonymRepo.findTopByTermEmbedding(vectorStr, topK) } returns listOf(synonym)
        every { synonymRepo.findByDescEmbeddingWithThreshold(vectorStr, threshold) } returns listOf(synonym)

        val context = QueryProcessingContext(query, query, "sid-1")

        // Act
        val result = step.execute(context)

        // Assert
        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).contains("Auth: Механизм входа")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.EXPANDED)).isTrue()

        val synonymsInfo = result.context.getMetadata<List<Map<String, Any>>>(QueryMetadataKeys.EXPANDED_SYNONYMS)
        assertThat(synonymsInfo).hasSize(1)
        assertThat(synonymsInfo!![0]["term"]).isEqualTo("Auth")
    }

    @Test
    @DisplayName("Фильтрация: синоним есть в топ-N по терму, но не прошел порог по описанию")
    fun `execute - filters out synonym if it fails description threshold`() {
        // Arrange
        val synonym = createMockSynonym(1L, "Auth", "Low similarity desc")

        every { synonymRepo.findTopByTermEmbedding(vectorStr, topK) } returns listOf(synonym)
        // Описание не проходит валидацию (возвращаем пустой список)
        every { synonymRepo.findByDescEmbeddingWithThreshold(vectorStr, threshold) } returns emptyList()

        val context = QueryProcessingContext(query, query, "sid-1")

        // Act
        val result = step.execute(context)

        // Assert
        assertThat(result.context.currentQuery).isEqualTo(query) // Запрос не изменился
        assertThat(result.context.hasMetadata(QueryMetadataKeys.EXPANDED)).isTrue()
    }

    @Test
    @DisplayName("Пропуск шага: если в контексте уже стоит флаг EXPANDED")
    fun `execute - skips if already expanded`() {
        // Arrange
        val context = QueryProcessingContext(query, query, "sid-1")
            .setMetadata(QueryMetadataKeys.EXPANDED, true)

        // Act
        val result = step.execute(context)

        // Assert
        verify { embeddingClient wasNot Called }
        assertThat(result.transitionKey).isEqualTo("SUCCESS")
    }

    @Test
    @DisplayName("Отказоустойчивость: при ошибке БД возвращаем оригинальный запрос")
    fun `execute - fallback to original query on repository exception`() {
        // Arrange
        every { synonymRepo.findTopByTermEmbedding(any(), any()) } throws RuntimeException("DB Down")

        val context = QueryProcessingContext(query, query, "sid-1")

        // Act
        val result = step.execute(context)

        // Assert
        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).isEqualTo(query)
        val lastStep = result.context.processingSteps.last()
        assertThat(lastStep.status).isEqualTo(ProcessingStepStatus.SUCCESS)
        assertThat(lastStep.output).contains("Ошибка расширения")
    }

    @Test
    @DisplayName("Количество синонимов: не более top-K даже если валидация вернула больше")
    fun `execute - limits result to top-K`() {
        // Arrange
        val synonyms = (1..5).map { createMockSynonym(it.toLong(), "Term$it", "Desc$it") }

        every { synonymRepo.findTopByTermEmbedding(vectorStr, topK) } returns synonyms.take(3)
        every { synonymRepo.findByDescEmbeddingWithThreshold(vectorStr, threshold) } returns synonyms

        val context = QueryProcessingContext(query, query, "sid-1")

        // Act
        val result = step.execute(context)

        // Assert
        val synonymsInfo = result.context.getMetadata<List<Map<String, Any>>>(QueryMetadataKeys.EXPANDED_SYNONYMS)
        assertThat(synonymsInfo).hasSize(3)
    }

    // Вспомогательный метод для генерации мока интерфейса проекции
    private fun createMockSynonym(id: Long, term: String, desc: String): SynonymDictionaryRepository.SynonymWithSimilarity {
        return mockk {
            every { getId() } returns id
            every { getTerm() } returns term
            every { getDescription() } returns desc
            every { getSourceNodeId() } returns 100L
            every { getModelName() } returns "test-model"
        }
    }
}