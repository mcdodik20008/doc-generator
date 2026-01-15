package com.bftcom.docgenerator.embedding.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmbeddingConfigTest {
    @Test
    fun `EmbeddingConfig - создается`() {
        assertThat(EmbeddingConfig()).isNotNull
    }
}

