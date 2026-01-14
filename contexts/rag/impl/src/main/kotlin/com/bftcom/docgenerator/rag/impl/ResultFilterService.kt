package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.embedding.api.SearchResult
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Сервис для фильтрации результатов векторного поиска по ключевым словам.
 * Использует простой поиск подстрок и TF-IDF-подобную оценку для фильтрации нерелевантных документов.
 */
@Component
class ResultFilterService {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Фильтрует результаты поиска, оставляя только те, которые содержат упоминания
     * извлеченных из запроса класса и метода.
     */
    fun filterResults(
        results: List<SearchResult>,
        processingContext: QueryProcessingContext,
    ): List<SearchResult> {
        // Получаем извлеченные класс и метод из контекста (если advisor заполнил)
        val extractionResult = processingContext.getMetadata<Map<*, *>>(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)

        val extractedClassName = (extractionResult?.get("className") as? String)?.takeIf { it.isNotBlank() }
        val extractedMethodName = (extractionResult?.get("methodName") as? String)?.takeIf { it.isNotBlank() }

        // Если advisor не извлек или извлек частично — дополняем из точных узлов
        var finalClassName = extractedClassName
        var finalMethodName = extractedMethodName

        val exactNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
        val nodes = exactNodes?.filterIsInstance<Node>().orEmpty()

        if (nodes.isNotEmpty()) {
            val firstMethodNode = nodes.firstOrNull { it.kind == NodeKind.METHOD }
            val firstClassNode = nodes.firstOrNull { it.kind == NodeKind.CLASS }

            if (finalMethodName.isNullOrBlank()) {
                finalMethodName = firstMethodNode?.name?.takeIf { it.isNotBlank() }
            }

            if (finalClassName.isNullOrBlank()) {
                // 1) если есть класс-узел — берем его имя/последний сегмент FQN
                finalClassName = firstClassNode?.name?.takeIf { it.isNotBlank() }
                    ?: firstClassNode?.fqn?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            }

            if (!finalClassName.isNullOrBlank() || !finalMethodName.isNullOrBlank()) {
                log.debug("Используем/дополняем имена из точных узлов: класс='{}', метод='{}'", finalClassName, finalMethodName)
            }
        }

        // Если не удалось определить вообще ничего — пропускаем фильтрацию
        if (finalClassName.isNullOrBlank() && finalMethodName.isNullOrBlank()) {
            log.debug("Нет информации об извлеченных классе/методе и нет применимых точных узлов, пропускаем фильтрацию")
            return results
        }

        log.debug("Фильтруем результаты по классу: '{}', методу: '{}'", finalClassName, finalMethodName)

        // Собираем ключевые слова для поиска
        val keywords = mutableListOf<String>()
        finalClassName?.let { keywords.add(it) }
        finalMethodName?.let { keywords.add(it) }

        // Фильтруем результаты в два этапа:
        // 1. Сначала ищем ТОЧНЫЕ совпадения
        // 2. Если точных совпадений нет, используем нечеткое совпадение
        val exactMatches = results.filter { result ->
            containsKeywordsExact(result.content, keywords, finalClassName, finalMethodName)
        }

        val filtered = if (exactMatches.isNotEmpty()) {
            // Если есть точные совпадения, используем ТОЛЬКО их
            log.info("Найдено {} точных совпадений, используем только их", exactMatches.size)
            exactMatches
        } else {
            // Если точных совпадений нет, используем нечеткое совпадение
            log.info("Точных совпадений не найдено, используем нечеткое совпадение")
            results.filter { result ->
                containsKeywordsFuzzy(result.content, keywords, finalClassName, finalMethodName)
            }
        }

        val removed = results.size - filtered.size
        if (removed > 0) {
            log.info("Отфильтровано {} результатов из {} (осталось {})", removed, results.size, filtered.size)
        }

        return filtered
    }

    /**
     * Проверяет, содержит ли документ нужные ключевые слова с ТОЧНЫМ совпадением.
     * Используется для строгой фильтрации, когда нужно исключить похожие классы.
     */
    private fun containsKeywordsExact(
        content: String,
        keywords: List<String>,
        className: String?,
        methodName: String?,
    ): Boolean {
        // Если есть и класс, и метод - требуем наличие ОБОИХ (строгая фильтрация)
        if (className != null && methodName != null) {
            val hasClass = containsKeywordExact(content, className)
            val hasMethod = containsKeywordExact(content, methodName)
            
            // Документ должен содержать оба с точным совпадением
            return hasClass && hasMethod
        }
        
        // Если только класс или только метод - проверяем точное совпадение
        return keywords.any { keyword ->
            containsKeywordExact(content, keyword)
        }
    }

    /**
     * Проверяет, содержит ли документ нужные ключевые слова с НЕЧЕТКИМ совпадением.
     * Используется как fallback, когда точных совпадений нет.
     */
    private fun containsKeywordsFuzzy(
        content: String,
        keywords: List<String>,
        className: String?,
        methodName: String?,
    ): Boolean {
        val lowerContent = content.lowercase()
        
        // Если есть и класс, и метод - требуем наличие ОБОИХ (но совпадение может быть нечетким)
        if (className != null && methodName != null) {
            val hasClass = containsKeywordFuzzy(lowerContent, className)
            val hasMethod = containsKeywordFuzzy(lowerContent, methodName)
            
            // Документ должен содержать оба
            return hasClass && hasMethod
        }
        
        // Если только класс или только метод - проверяем наличие
        return keywords.any { keyword ->
            containsKeywordFuzzy(lowerContent, keyword)
        }
    }

    /**
     * Проверяет наличие ключевого слова с ТОЧНЫМ совпадением.
     * Использует границы слов, чтобы исключить похожие классы.
     * 
     * Например:
     * - "Step15Processor" совпадает с "Step15Processor"
     * - "Step15Processor" НЕ совпадает с "Step16Processor"
     * - "Step15Processor" НЕ совпадает с "Step15ProcessorHelper"
     */
    private fun containsKeywordExact(content: String, keyword: String): Boolean {
        if (keyword.isBlank()) {
            return false
        }
        
        val lowerKeyword = keyword.lowercase().trim()
        
        // Для коротких ключевых слов (1-2 символа) пропускаем фильтрацию
        if (lowerKeyword.length <= 2) {
            return true
        }
        
        // Экранируем специальные символы для regex
        val escapedKeyword = Regex.escape(lowerKeyword)
        
        // ТОЧНОЕ совпадение с границами слов (\b)
        // Это гарантирует, что "Step15Processor" не совпадет с "Step16Processor"
        val exactPattern = "\\b$escapedKeyword\\b"
        
        return Regex(exactPattern, RegexOption.IGNORE_CASE).containsMatchIn(content)
    }

    /**
     * Проверяет наличие ключевого слова с НЕЧЕТКИМ совпадением.
     * Используется как fallback, когда точных совпадений нет.
     * Разрешает частичные совпадения и совпадения частей составных слов.
     */
    private fun containsKeywordFuzzy(content: String, keyword: String): Boolean {
        if (keyword.isBlank()) {
            return false
        }
        
        val lowerKeyword = keyword.lowercase().trim()
        
        // Для коротких ключевых слов (1-2 символа) пропускаем фильтрацию
        if (lowerKeyword.length <= 2) {
            return true
        }
        
        // Простое вхождение (case-insensitive)
        if (content.contains(lowerKeyword, ignoreCase = true)) {
            return true
        }
        
        // Поиск с учетом возможных вариантов написания (CamelCase, snake_case и т.д.)
        val wordParts = splitCamelCase(keyword)
        if (wordParts.size > 1) {
            // Если ключевое слово составное, проверяем наличие хотя бы одной значимой части
            val significantParts = wordParts.filter { it.length > 2 }.distinct()
            if (significantParts.isNotEmpty()) {
                // Чтобы не матчить по одному общему суффиксу вроде "Service",
                // для составных слов требуем наличие ВСЕХ значимых частей.
                if (significantParts.size > 1) {
                    return significantParts.all { part ->
                        content.contains(part, ignoreCase = true)
                    }
                }

                // Если значимая часть одна — достаточно ее наличия
                return content.contains(significantParts.first(), ignoreCase = true)
            }
        }
        
        return false
    }

    /**
     * Разбивает CamelCase строку на части.
     */
    private fun splitCamelCase(str: String): List<String> {
        return str.split(Regex("(?=[A-Z])|_|-")).filter { it.isNotBlank() }
            .map { it.lowercase() }
    }

    /**
     * Вычисляет релевантность документа на основе TF-IDF-подобной метрики.
     * Можно использовать для дополнительной сортировки после фильтрации.
     */
    fun calculateRelevance(content: String, keywords: List<String>): Double {
        val lowerContent = content.lowercase()
        val words = lowerContent.split(Regex("\\W+")).filter { it.isNotBlank() }
        val totalWords = words.size.toDouble()
        
        if (totalWords == 0.0) {
            return 0.0
        }

        var score = 0.0
        keywords.forEach { keyword ->
            val lowerKeyword = keyword.lowercase()
            val termFrequency = words.count { it.contains(lowerKeyword) || lowerKeyword.contains(it) } / totalWords
            
            // Простая оценка релевантности (TF без IDF, так как у нас нет корпуса)
            score += termFrequency * 10.0 // Увеличиваем вес для видимости
        }

        return score
    }
}

