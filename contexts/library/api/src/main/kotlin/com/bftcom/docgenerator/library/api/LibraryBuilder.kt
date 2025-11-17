package com.bftcom.docgenerator.library.api

import java.io.File

/**
 * Интерфейс для построения графа библиотек из jar-файлов.
 */
interface LibraryBuilder {
    /**
     * Строит граф библиотек из списка jar-файлов (classpath).
     * Для каждого jar:
     * 1. Извлекает координаты (groupId:artifactId:version)
     * 2. Парсит байткод и создаёт LibraryNode
     * 3. Сохраняет в БД (или обновляет, если уже существует)
     *
     * @param classpath Список jar-файлов для анализа
     * @return Результат построения (статистика: сколько библиотек обработано, сколько нод создано)
     */
    fun buildLibraries(classpath: List<File>): LibraryBuildResult
}
