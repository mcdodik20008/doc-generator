package com.bftcom.docgenerator.postprocessor.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PpUtilTest {
    @Test
    fun `tokenCount - считает токены по непустым фрагментам`() {
        assertThat(PpUtil.tokenCount("a b  c")).isEqualTo(3)
        assertThat(PpUtil.tokenCount("   ")).isEqualTo(0)
    }

    @Test
    fun `spanCharsRange - диапазон от 0 до length`() {
        assertThat(PpUtil.spanCharsRange("abcd")).isEqualTo("[0,4)")
    }

    @Test
    fun `usesMarkdown - находит базовые паттерны markdown`() {
        assertThat(PpUtil.usesMarkdown("# Title")).isTrue()
        assertThat(PpUtil.usesMarkdown("- item")).isTrue()
        assertThat(PpUtil.usesMarkdown("**bold**")).isTrue()
        assertThat(PpUtil.usesMarkdown("plain text")).isFalse()
    }

    @Test
    fun `explainQualityJson - выставляет grade по порогам`() {
        assertThat(PpUtil.explainQualityJson(tokens = 10, len = 10)).contains("\"grade\":\"C\"")
        assertThat(PpUtil.explainQualityJson(tokens = 120, len = 10)).contains("\"grade\":\"B\"")
        assertThat(PpUtil.explainQualityJson(tokens = 300, len = 10)).contains("\"grade\":\"A\"")
    }

    @Test
    fun `explainMd - включает summary и preview`() {
        val md = PpUtil.explainMd("hello\nworld", tokens = 2)
        assertThat(md).contains("### Summary")
        assertThat(md).contains("tokens: 2")
        assertThat(md).contains("#### Preview")
    }
}

