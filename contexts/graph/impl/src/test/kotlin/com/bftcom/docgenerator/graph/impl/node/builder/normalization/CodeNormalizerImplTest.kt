package com.bftcom.docgenerator.graph.impl.node.builder.normalization

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeNormalizerImplTest {
    private val normalizer = CodeNormalizerImpl()

    @Test
    fun `normalize - null возвращает null`() {
        assertThat(normalizer.normalize(null, maxSize = 10)).isNull()
    }

    @Test
    fun `normalize - не обрезает если размер меньше maxSize`() {
        val src = "abc"
        assertThat(normalizer.normalize(src, maxSize = 10)).isSameAs(src)
    }

    @Test
    fun `normalize - обрезает и добавляет суффикс если превышен maxSize`() {
        val src = "0123456789ABCDEF"
        val normalized = normalizer.normalize(src, maxSize = 10)

        assertThat(normalized).isNotNull
        assertThat(normalized!!).startsWith("0123456789")
        assertThat(normalized).endsWith("\n... [truncated]")
        assertThat(normalized).contains("0123456789")
    }

    @Test
    fun `countLines - пустая строка это 0`() {
        assertThat(normalizer.countLines("")).isEqualTo(0)
    }

    @Test
    fun `countLines - корректно считает строки и нормализует CRLF`() {
        assertThat(normalizer.countLines("a")).isEqualTo(1)
        assertThat(normalizer.countLines("a\nb")).isEqualTo(2)
        assertThat(normalizer.countLines("a\r\nb\r\nc")).isEqualTo(3)
    }
}

