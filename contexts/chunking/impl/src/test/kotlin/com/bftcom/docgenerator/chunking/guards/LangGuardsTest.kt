package com.bftcom.docgenerator.chunking.guards

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LangGuardsTest {

    @Test
    fun `hasCyrillic - возвращает true для кириллицы`() {
        assertThat(LangGuards.hasCyrillic("Привет")).isTrue()
        assertThat(LangGuards.hasCyrillic("Hello Привет")).isTrue()
        assertThat(LangGuards.hasCyrillic("Тест123")).isTrue()
    }

    @Test
    fun `hasCyrillic - возвращает false для латиницы`() {
        assertThat(LangGuards.hasCyrillic("Hello")).isFalse()
        assertThat(LangGuards.hasCyrillic("123")).isFalse()
        assertThat(LangGuards.hasCyrillic("")).isFalse()
    }

    @Test
    fun `hasCyrillic - возвращает false для пустой строки`() {
        assertThat(LangGuards.hasCyrillic("")).isFalse()
    }

    @Test
    fun `hasLatin - возвращает true для латиницы`() {
        assertThat(LangGuards.hasLatin("Hello")).isTrue()
        assertThat(LangGuards.hasLatin("Привет Hello")).isTrue()
        assertThat(LangGuards.hasLatin("Test123")).isTrue()
    }

    @Test
    fun `hasLatin - возвращает false для кириллицы`() {
        assertThat(LangGuards.hasLatin("Привет")).isFalse()
        assertThat(LangGuards.hasLatin("123")).isFalse()
        assertThat(LangGuards.hasLatin("")).isFalse()
    }

    @Test
    fun `hasLatin - возвращает false для пустой строки`() {
        assertThat(LangGuards.hasLatin("")).isFalse()
    }

    @Test
    fun `cyrillicRatio - возвращает 1 0 для чистой кириллицы`() {
        assertThat(LangGuards.cyrillicRatio("Привет")).isEqualTo(1.0)
        assertThat(LangGuards.cyrillicRatio("Тест")).isEqualTo(1.0)
    }

    @Test
    fun `cyrillicRatio - возвращает 0 0 для чистой латиницы`() {
        assertThat(LangGuards.cyrillicRatio("Hello")).isEqualTo(0.0)
        assertThat(LangGuards.cyrillicRatio("Test")).isEqualTo(0.0)
    }

    @Test
    fun `cyrillicRatio - возвращает 0 5 для смешанного текста`() {
        // "Тест" = 4 буквы (кириллица), "Test" = 4 буквы (латиница) = 4/8 = 0.5
        assertThat(LangGuards.cyrillicRatio("ТестTest")).isEqualTo(0.5)
        // "Мир" = 3 буквы (кириллица), "World" = 5 букв (латиница) = 3/8 = 0.375
        assertThat(LangGuards.cyrillicRatio("МирWorld")).isCloseTo(0.375, org.assertj.core.data.Offset.offset(0.01))
        // "Привет" = 6 букв (кириллица), "Hello" = 5 букв (латиница) = 6/11 ≈ 0.545
        assertThat(LangGuards.cyrillicRatio("ПриветHello")).isCloseTo(0.545, org.assertj.core.data.Offset.offset(0.01))
    }

    @Test
    fun `cyrillicRatio - возвращает 0 0 для пустой строки`() {
        assertThat(LangGuards.cyrillicRatio("")).isEqualTo(0.0)
    }

    @Test
    fun `cyrillicRatio - игнорирует не-буквы`() {
        // "Привет" = 6 букв (кириллица), "Hello" = 5 букв (латиница), 123 - не буквы
        // Итого: 6 кириллических из 11 букв = 6/11 ≈ 0.545
        assertThat(LangGuards.cyrillicRatio("Привет123Hello")).isCloseTo(0.545, org.assertj.core.data.Offset.offset(0.01))
        // "Тест" = 4 буквы (кириллица), "Test" = 4 буквы (латиница), !!! - не буквы
        // Итого: 4 кириллических из 8 букв = 4/8 = 0.5
        assertThat(LangGuards.cyrillicRatio("Тест!!!Test")).isEqualTo(0.5)
        // "Привет" = 6 букв (кириллица), "Hello" = 5 букв (латиница), пробелы и 123 - не буквы
        // Итого: 6 кириллических из 11 букв = 6/11 ≈ 0.545
        assertThat(LangGuards.cyrillicRatio("Привет 123 Hello")).isCloseTo(0.545, org.assertj.core.data.Offset.offset(0.01))
    }

    @Test
    fun `cyrillicRatio - корректно вычисляет долю для длинного текста`() {
        // "Привет" = 6 букв, повторено 10 раз = 60 кириллических букв
        // "Hello" = 5 букв, повторено 5 раз = 25 латинских букв
        // Итого: 60 кириллических из 85 букв = 60/85 ≈ 0.7059
        val text = "Привет".repeat(10) + "Hello".repeat(5)
        val ratio = LangGuards.cyrillicRatio(text)
        assertThat(ratio).isCloseTo(0.706, org.assertj.core.data.Offset.offset(0.01))
    }

    @Test
    fun `isRussianEnough - возвращает true для русского текста с достаточной долей`() {
        assertThat(LangGuards.isRussianEnough("Привет мир")).isTrue()
        assertThat(LangGuards.isRussianEnough("Это тестовый текст на русском языке")).isTrue()
        assertThat(LangGuards.isRussianEnough("Привет123", 0.6)).isTrue()
    }

    @Test
    fun `isRussianEnough - возвращает false для текста без кириллицы`() {
        assertThat(LangGuards.isRussianEnough("Hello world")).isFalse()
        assertThat(LangGuards.isRussianEnough("123")).isFalse()
        assertThat(LangGuards.isRussianEnough("")).isFalse()
    }

    @Test
    fun `isRussianEnough - возвращает false если доля кириллицы ниже порога`() {
        assertThat(LangGuards.isRussianEnough("ПриветHelloHelloHello", 0.6)).isFalse()
        assertThat(LangGuards.isRussianEnough("TestПривет", 0.8)).isFalse()
    }

    @Test
    fun `isRussianEnough - использует порог по умолчанию 0 6`() {
        // Текст с долей кириллицы ровно 0.6
        val text = "Привет".repeat(6) + "Hello".repeat(4) // 36/60 = 0.6
        assertThat(LangGuards.isRussianEnough(text)).isTrue()

        // Текст с долей кириллицы меньше 0.6
        val text2 = "Привет".repeat(5) + "Hello".repeat(5) // 30/60 = 0.5
        assertThat(LangGuards.isRussianEnough(text2)).isFalse()
    }

    @Test
    fun `isRussianEnough - корректно работает с кастомным порогом`() {
        val text = "ПриветHello" // доля кириллицы = 0.5

        assertThat(LangGuards.isRussianEnough(text, minRatio = 0.4)).isTrue()
        assertThat(LangGuards.isRussianEnough(text, minRatio = 0.5)).isTrue()
        assertThat(LangGuards.isRussianEnough(text, minRatio = 0.6)).isFalse()
    }

    @Test
    fun `isRussianEnough - возвращает false для пустой строки`() {
        assertThat(LangGuards.isRussianEnough("")).isFalse()
        assertThat(LangGuards.isRussianEnough("", 0.0)).isFalse()
    }
}
