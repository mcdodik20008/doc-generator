package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueryNormalizationAdvisorTest {
    private val advisor = QueryNormalizationAdvisor()

    @Test
    fun `process - нормализует пробелы и убирает пунктуацию в конце`() {
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "  Foo   bar??  ",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.currentQuery).isEqualTo("Foo bar")
        assertThat(context.getMetadata<Boolean>(QueryMetadataKeys.NORMALIZED)).isTrue
        assertThat(context.getMetadata<String>(QueryMetadataKeys.PREVIOUS_QUERY)).isEqualTo("  Foo   bar??  ")
        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryNormalization")
        assertThat(context.processingSteps[0].input).isEqualTo("  Foo   bar??  ")
        assertThat(context.processingSteps[0].output).isEqualTo("Foo bar")
    }

    @Test
    fun `process - ничего не меняет если запрос уже нормализован`() {
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "Foo bar",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.currentQuery).isEqualTo("Foo bar")
        assertThat(context.hasMetadata(QueryMetadataKeys.NORMALIZED)).isFalse
        assertThat(context.processingSteps).isEmpty()
    }
}

