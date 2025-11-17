package com.bftcom.docgenerator.library.api

import java.io.File

/**
 * Парсер байткода из jar-файлов.
 * Извлекает структуру классов, методов, полей, аннотаций из .class файлов.
 */
interface BytecodeParser {
    /**
     * Парсит jar-файл и возвращает список "сырых" нод библиотеки.
     * @param jarFile Путь к jar-файлу
     * @return Список RawLibraryNode (классы, методы, поля и т.д.)
     */
    fun parseJar(jarFile: File): List<RawLibraryNode>
}
