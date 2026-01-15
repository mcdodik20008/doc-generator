package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueryKeywordExtractionAdvisorTest {
    private val advisor = QueryKeywordExtractionAdvisor()

    @Test
    fun `process - извлекает ключевые слова и убирает стоп-слова`() {
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "Покажи как работает UserService и метод getUser, где это находится?",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue

        val keywords = context.getMetadata<List<String>>(QueryMetadataKeys.KEYWORDS)
        assertThat(keywords).isNotNull
        assertThat(keywords!!).contains("userservice", "getuser")
        assertThat(keywords).doesNotContain("как", "покажи", "где", "это", "и")

        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryKeywordExtraction")
        assertThat(context.processingSteps[0].input).isEqualTo(context.currentQuery)
        assertThat(context.processingSteps[0].output).contains("userservice")
    }

    @Test
    fun `process - не пишет метаданные если ключевых слов нет`() {
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "как где это и",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.hasMetadata(QueryMetadataKeys.KEYWORDS)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }
}

