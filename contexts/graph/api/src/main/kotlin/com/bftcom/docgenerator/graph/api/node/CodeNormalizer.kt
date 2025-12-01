package com.bftcom.docgenerator.graph.api.node

/**
 * Нормализатор исходного кода.
 * Отвечает за нормализацию и обрезку кода до максимального размера.
 */
interface CodeNormalizer {
    /**
     * Нормализует исходный код, обрезая его до максимального размера при необходимости.
     * @param sourceCode Исходный код
     * @param maxSize Максимальный размер в байтах
     * @return Нормализованный код или null, если исходный код был null
     */
    fun normalize(sourceCode: String?, maxSize: Int): String?

    /**
     * Подсчитывает количество строк в нормализованном коде.
     * @param sourceCode Исходный код
     * @return Количество строк (минимум 0)
     */
    fun countLines(sourceCode: String): Int
}

