package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueryQualityAdvisorTest {
    private val advisor = QueryQualityAdvisor()

    @Test
    fun `process - пишет quality метрики и шаг`() {
        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "Как работает класс UserService и метод getUser?",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue

        @Suppress("UNCHECKED_CAST")
        val metrics = context.getMetadata<Map<String, Any>>(QueryMetadataKeys.QUALITY_METRICS)
        assertThat(metrics).isNotNull
        assertThat(metrics!!).containsKeys("length", "wordCount", "hasQuestionWord", "techTermCount", "qualityScore")
        assertThat(metrics["length"]).isEqualTo(context.currentQuery.length)
        assertThat(metrics["qualityScore"]).isInstanceOf(Number::class.java)
        assertThat((metrics["qualityScore"] as Number).toDouble()).isBetween(0.0, 1.0)

        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("QueryQuality")
        assertThat(context.processingSteps[0].output).contains("Quality score:")
    }
}

