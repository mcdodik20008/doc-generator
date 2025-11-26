package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.domain.node.Node
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
        // Получаем извлеченные класс и метод из контекста
        val extractionResult = processingContext.getMetadata<Map<*, *>>(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)
        
        if (extractionResult == null) {
            log.debug("Нет информации об извлеченных классе/методе, пропускаем фильтрацию")
            return results
        }

        val className = (extractionResult["className"] as? String)?.takeIf { it.isNotBlank() }
        val methodName = (extractionResult["methodName"] as? String)?.takeIf { it.isNotBlank() }

        // Если класс/метод не извлечены, пытаемся использовать имена из точных узлов
        var finalClassName = className
        var finalMethodName = methodName
        
        if (finalClassName.isNullOrBlank() && finalMethodName.isNullOrBlank()) {
            val exactNodes = processingContext.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
            val nodes = exactNodes?.filterIsInstance<Node>() ?: emptyList()
            
            if (nodes.isNotEmpty()) {
                // Берем имена из первого найденного узла
                val firstNode = nodes.first()
                if (finalClassName == null) {
                    // Пытаемся извлечь имя класса из FQN
                    val fqn = firstNode.fqn
                    val lastDot = fqn.lastIndexOf('.')
                    if (lastDot > 0) {
                        val parentFqn = fqn.substring(0, lastDot)
                        finalClassName = parentFqn.substringAfterLast('.')
                    } else {
                        finalClassName = fqn
                    }
                }
                if (finalMethodName == null && firstNode.kind.name == "METHOD") {
                    finalMethodName = firstNode.name
                }
                
                log.debug("Используем имена из точных узлов: класс='{}', метод='{}'", finalClassName, finalMethodName)
            } else {
                log.debug("Класс и метод не извлечены и нет точных узлов, пропускаем фильтрацию")
                return results
            }
        }

        log.debug("Фильтруем результаты по классу: '{}', методу: '{}'", finalClassName, finalMethodName)

        // Собираем ключевые слова для поиска
        val keywords = mutableListOf<String>()
        finalClassName?.let { keywords.add(it) }
        finalMethodName?.let { keywords.add(it) }

        // Фильтруем результаты
        val filtered = results.filter { result ->
            containsKeywords(result.content, keywords, finalClassName, finalMethodName)
        }

        val removed = results.size - filtered.size
        if (removed > 0) {
            log.info("Отфильтровано {} результатов из {} (осталось {})", removed, results.size, filtered.size)
        }

        return filtered
    }

    /**
     * Проверяет, содержит ли документ нужные ключевые слова.
     * Использует гибкий поиск с учетом регистра и частичных совпадений.
     */
    private fun containsKeywords(
        content: String,
        keywords: List<String>,
        className: String?,
        methodName: String?,
    ): Boolean {
        val lowerContent = content.lowercase()
        
        // Если есть и класс, и метод - проверяем наличие хотя бы одного
        if (className != null && methodName != null) {
            val hasClass = containsKeyword(lowerContent, className)
            val hasMethod = containsKeyword(lowerContent, methodName)
            
            // Документ должен содержать хотя бы один из них
            // Это позволяет находить документы, где упоминается класс или метод отдельно
            return hasClass || hasMethod
        }
        
        // Если только класс или только метод - проверяем наличие
        return keywords.any { keyword ->
            containsKeyword(lowerContent, keyword)
        }
    }

    /**
     * Проверяет наличие ключевого слова в тексте с учетом различных вариантов написания.
     */
    private fun containsKeyword(content: String, keyword: String): Boolean {
        if (keyword.isBlank()) {
            return false
        }
        
        val lowerKeyword = keyword.lowercase().trim()
        
        // Прямое вхождение (case-insensitive)
        if (content.contains(lowerKeyword, ignoreCase = true)) {
            return true
        }
        
        // Поиск с учетом возможных вариантов написания (CamelCase, snake_case и т.д.)
        val wordParts = splitCamelCase(keyword)
        if (wordParts.size > 1) {
            // Если ключевое слово составное, проверяем наличие хотя бы одной значимой части
            // (чтобы не отфильтровывать документы с частичными совпадениями)
            val significantParts = wordParts.filter { it.length > 2 }
            if (significantParts.isNotEmpty()) {
                // Документ должен содержать хотя бы одну значимую часть
                return significantParts.any { part ->
                    content.contains(part.lowercase(), ignoreCase = true)
                }
            }
        }
        
        // Если ключевое слово короткое (1-2 символа), пропускаем фильтрацию по нему
        // чтобы не отфильтровывать документы из-за слишком общих слов
        if (lowerKeyword.length <= 2) {
            return true
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

