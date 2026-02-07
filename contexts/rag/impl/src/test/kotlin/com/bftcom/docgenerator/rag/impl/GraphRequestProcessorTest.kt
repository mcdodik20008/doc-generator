package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.steps.QueryStep
import com.bftcom.docgenerator.rag.impl.steps.StepResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphRequestProcessorTest {
    @Test
    fun `process - выполняет шаг и завершает`() {
        val step = mockk<QueryStep>()
        every { step.type } returns ProcessingStepType.NORMALIZATION
        every { step.execute(any()) } answers {
            StepResult(firstArg(), "SUCCESS")
        }
        every { step.getTransitions() } returns mapOf(
            "SUCCESS" to ProcessingStepType.COMPLETED,
        )

        val processor = GraphRequestProcessor(listOf(step))
        val result = processor.process("query", "session")

        assertThat(result.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS))
            .isEqualTo(ProcessingStepType.COMPLETED.name)
    }

    @Test
    fun `process - обнаруживает цикл и завершает FAILED`() {
        val step = mockk<QueryStep>()
        every { step.type } returns ProcessingStepType.NORMALIZATION
        every { step.execute(any()) } answers {
            StepResult(firstArg(), "FOUND")
        }
        every { step.getTransitions() } returns mapOf(
            "FOUND" to ProcessingStepType.NORMALIZATION, // Цикл
        )

        val processor = GraphRequestProcessor(listOf(step))
        val result = processor.process("query", "session")

        assertThat(result.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS))
            .isEqualTo(ProcessingStepType.FAILED.name)
    }

    @Test
    fun `process - ошибка в шаге приводит к FAILED и записи ошибки`() {
        val step = mockk<QueryStep>()
        every { step.type } returns ProcessingStepType.NORMALIZATION
        every { step.execute(any()) } throws RuntimeException("boom")
        every { step.getTransitions() } returns emptyMap()

        val processor = GraphRequestProcessor(listOf(step))
        val result = processor.process("query", "session")

        val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}${ProcessingStepType.NORMALIZATION.name}"
        assertThat(result.metadata[errorKey]).isEqualTo("boom")
        assertThat(result.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS))
            .isEqualTo(ProcessingStepType.FAILED.name)
    }

    @Test
    fun `process - неизвестный transitionKey завершает FAILED`() {
        val step = mockk<QueryStep>()
        every { step.type } returns ProcessingStepType.NORMALIZATION
        every { step.execute(any()) } answers {
            StepResult(firstArg(), "UNKNOWN_KEY")
        }
        every { step.getTransitions() } returns mapOf(
            "SUCCESS" to ProcessingStepType.COMPLETED,
        )

        val processor = GraphRequestProcessor(listOf(step))
        val result = processor.process("query", "session")

        assertThat(result.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS))
            .isEqualTo(ProcessingStepType.FAILED.name)
    }

    @Test
    fun `process - отсутствующий шаг завершает FAILED`() {
        val processor = GraphRequestProcessor(emptyList())
        val result = processor.process("query", "session")

        assertThat(result.getMetadata<String>(QueryMetadataKeys.PROCESSING_STATUS))
            .isEqualTo(ProcessingStepType.FAILED.name)
    }
}
