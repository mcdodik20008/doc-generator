package com.bftcom.docgenerator.library.api.bytecode

import java.io.File

/**
 * Анализатор байткода для поиска HTTP-вызовов (WebClient, RestTemplate).
 * Реализует три фазы:
 * - Фаза 1: Поиск HTTP-вызовов и извлечение URL
 * - Фаза 2: Построение call graph и подъем до родительских клиентов
 * - Фаза 3: Сборка сводки по каждому родительскому методу
 */
interface HttpBytecodeAnalyzer {
    /**
     * Анализирует jar-файл и возвращает результат анализа.
     */
    fun analyzeJar(jarFile: File): BytecodeAnalysisResult
}
