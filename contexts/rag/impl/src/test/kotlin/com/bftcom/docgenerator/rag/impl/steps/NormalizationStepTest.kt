package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NormalizationStepTest {
    private val step = NormalizationStep()

    @Test
    fun `execute - нормализует пробелы и убирает пунктуацию`() {
        val context = QueryProcessingContext(
            originalQuery = "  Foo   bar??  ",
            currentQuery = "  Foo   bar??  ",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).isEqualTo("Foo bar")
        assertThat(result.context.getMetadata<Boolean>(QueryMetadataKeys.NORMALIZED)).isTrue
        assertThat(result.context.processingSteps).hasSize(1)
        assertThat(result.context.processingSteps[0].advisorName).isEqualTo("NormalizationStep")
        assertThat(result.context.processingSteps[0].input).isEqualTo("  Foo   bar??  ")
        assertThat(result.context.processingSteps[0].output).isEqualTo("Foo bar")
    }

    @Test
    fun `execute - не меняет уже нормализованный запрос`() {
        val context = QueryProcessingContext(
            originalQuery = "Foo bar",
            currentQuery = "Foo bar",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")
        assertThat(result.context.currentQuery).isEqualTo("Foo bar")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.NORMALIZED)).isFalse
        assertThat(result.context.processingSteps).isEmpty()
    }

    @Test
    fun `execute - убирает разные знаки препинания в конце`() {
        val testCases = listOf(
            "Test?" to "Test",
            "Test!" to "Test",
            "Test." to "Test",
            "Test," to "Test",
            "Test;" to "Test",
            "Test:" to "Test",
            "Test???" to "Test",
        )

        testCases.forEach { (input, expected) ->
            val context = QueryProcessingContext(
                originalQuery = input,
                currentQuery = input,
                sessionId = "s-1",
            )

            val result = step.execute(context)

            assertThat(result.context.currentQuery).isEqualTo(expected)
        }
    }
}
