package com.bftcom.docgenerator.chunking.guards

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LangGuardsTest {

    @Test
    fun `hasCyrillic - определяет кириллицу`() {
        assertTrue(LangGuards.hasCyrillic("привет"))
        assertTrue(LangGuards.hasCyrillic("mix Привет mix"))
        assertFalse(LangGuards.hasCyrillic("hello"))
    }

    @Test
    fun `hasLatin - определяет латиницу`() {
        assertTrue(LangGuards.hasLatin("hello"))
        assertTrue(LangGuards.hasLatin("mix Привет mix"))
        assertFalse(LangGuards.hasLatin("привет"))
    }

    @Test
    fun `cyrillicRatio - считает долю только среди букв`() {
        // 3 кириллических + 2 латинских = 5 букв
        val ratio = LangGuards.cyrillicRatio("абвAB 123 !!!")
        assertTrue(ratio > 0.5)
        assertTrue(ratio < 1.0)
    }

    @Test
    fun `isRussianEnough - требует кириллицу и порог доли`() {
        assertTrue(LangGuards.isRussianEnough("это русский текст"))
        assertFalse(LangGuards.isRussianEnough("hello world"))
        assertFalse(LangGuards.isRussianEnough("абвABC", minRatio = 0.9))
    }
}

