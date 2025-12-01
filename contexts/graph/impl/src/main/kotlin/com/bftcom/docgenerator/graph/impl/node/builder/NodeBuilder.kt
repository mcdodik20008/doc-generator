package com.bftcom.docgenerator.graph.impl.node.builder

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.node.CodeHasher
import com.bftcom.docgenerator.graph.api.node.CodeNormalizer
import com.bftcom.docgenerator.graph.api.node.NodeUpdateData
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import com.bftcom.docgenerator.graph.api.node.NodeValidator
import com.bftcom.docgenerator.graph.impl.node.builder.cache.NodeCache
import com.bftcom.docgenerator.graph.impl.node.builder.stats.NodeBuilderStats
import com.bftcom.docgenerator.graph.impl.node.builder.stats.NodeBuilderStatsManager
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Строитель нод - отвечает за создание и обновление Node сущностей.
 * Оркестрирует работу различных компонентов через композицию.
 */
class NodeBuilder(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
    private val validator: NodeValidator,
    private val codeNormalizer: CodeNormalizer,
    private val codeHasher: CodeHasher,
    private val updateStrategy: NodeUpdateStrategy,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Менеджер статистики
    private val statsManager = NodeBuilderStatsManager()

    // Кэш существующих нод для избежания N+1 запросов
    private val nodeCache = NodeCache()

    // Максимальный размер sourceCode (10MB)
    private val maxSourceCodeSize = 10 * 1024 * 1024

    fun upsertNode(
        fqn: String,
        kind: NodeKind,
        name: String?,
        packageName: String?,
        parent: Node?,
        lang: Lang,
        filePath: String?,
        span: IntRange?,
        signature: String?,
        sourceCode: String?,
        docComment: String?,
        meta: NodeMeta,
    ): Node {
        val applicationId = requireNotNull(application.id) { "Application must have an ID" }

        // Валидация входных данных
        validator.validate(fqn, span, parent, sourceCode, applicationId)

        // Кэшируем запросы к БД для избежания N+1 проблемы
        val existing =
            nodeCache.getOrCompute(fqn) {
                nodeRepo.findByApplicationIdAndFqn(applicationId, fqn)
            }

        val metaMap = toMetaMap(meta)

        // Нормализуем исходный код
        val normalizedSourceCode = codeNormalizer.normalize(sourceCode, maxSourceCodeSize)

        // Вычисляем lineEnd на основе нормализованного кода
        val lineStart: Int? = span?.first
        var lineEnd: Int? = span?.last
        if (normalizedSourceCode?.isNotEmpty() == true && lineStart != null) {
            lineEnd = lineStart + codeNormalizer.countLines(normalizedSourceCode) - 1
        }

        // Вычисляем хеш исходного кода (используем оригинальный, не обрезанный)
        val codeHash = codeHasher.computeHash(sourceCode)

        return if (existing == null) {
            // Создание новой ноды
            createNewNode(
                fqn = fqn,
                kind = kind,
                name = name,
                packageName = packageName,
                parent = parent,
                lang = lang,
                filePath = filePath,
                lineStart = lineStart,
                lineEnd = lineEnd,
                normalizedSourceCode = normalizedSourceCode,
                docComment = docComment,
                signature = signature,
                codeHash = codeHash,
                metaMap = metaMap,
            )
        } else {
            // Обновление существующей ноды
            updateExistingNode(
                existing = existing,
                name = name,
                packageName = packageName,
                kind = kind,
                lang = lang,
                parent = parent,
                filePath = filePath,
                lineStart = lineStart,
                lineEnd = lineEnd,
                normalizedSourceCode = normalizedSourceCode,
                docComment = docComment,
                signature = signature,
                codeHash = codeHash,
                metaMap = metaMap,
            )
        }
    }

    private fun createNewNode(
        fqn: String,
        kind: NodeKind,
        name: String?,
        packageName: String?,
        parent: Node?,
        lang: Lang,
        filePath: String?,
        lineStart: Int?,
        lineEnd: Int?,
        normalizedSourceCode: String?,
        docComment: String?,
        signature: String?,
        codeHash: String?,
        metaMap: Map<String, Any>,
    ): Node {
        log.debug("Creating new node: kind={}, fqn={}, file={}", kind, fqn, filePath)
        try {
            val newNode =
                nodeRepo.save(
                    Node(
                        id = null,
                        application = application,
                        fqn = fqn,
                        name = name,
                        packageName = packageName,
                        kind = kind,
                        lang = lang,
                        parent = parent,
                        filePath = filePath,
                        lineStart = lineStart,
                        lineEnd = lineEnd,
                        sourceCode = normalizedSourceCode,
                        docComment = docComment,
                        signature = signature,
                        codeHash = codeHash,
                        meta = metaMap,
                    ),
                )
            // Обновляем кэш
            nodeCache.put(fqn, newNode)
            statsManager.incrementCreated()
            log.trace("Node created: id={}, fqn={}, hash={}", newNode.id, fqn, codeHash?.take(8))
            return newNode
        } catch (e: Exception) {
            log.error("Failed to save new node: kind={}, fqn={}, error={}", kind, fqn, e.message, e)
            throw e
        }
    }

    private fun updateExistingNode(
        existing: Node,
        name: String?,
        packageName: String?,
        kind: NodeKind,
        lang: Lang,
        parent: Node?,
        filePath: String?,
        lineStart: Int?,
        lineEnd: Int?,
        normalizedSourceCode: String?,
        docComment: String?,
        signature: String?,
        codeHash: String?,
        metaMap: Map<String, Any>,
    ): Node {
        log.debug("Updating existing node: id={}, kind={}, fqn={}", existing.id, kind, existing.fqn)

        val updateData =
            NodeUpdateData(
                name = name,
                packageName = packageName,
                kind = kind,
                lang = lang,
                parent = parent,
                filePath = filePath,
                lineStart = lineStart,
                lineEnd = lineEnd,
                sourceCode = normalizedSourceCode,
                docComment = docComment,
                signature = signature,
                codeHash = codeHash,
                meta = metaMap,
            )

        // Проверяем наличие изменений ДО обновления
        val hasChanges = updateStrategy.hasChanges(existing, updateData)

        if (!hasChanges) {
            log.trace("Node unchanged: id={}, fqn={}, skipping save", existing.id, existing.fqn)
            statsManager.incrementSkipped()
            return existing
        }

        // Обновляем узел
        val updated = updateStrategy.update(existing, updateData)

        log.debug("Node updated: id={}, fqn={}, changes detected", existing.id, existing.fqn)
        try {
            val saved = nodeRepo.save(updated)
            // Обновляем кэш
            nodeCache.put(existing.fqn, saved)
            statsManager.incrementUpdated()
            return saved
        } catch (e: Exception) {
            log.error("Failed to update node: id={}, fqn={}, error={}", existing.id, existing.fqn, e.message, e)
            throw e
        }
    }

    /**
     * Получить статистику операций (для логирования на уровне выше).
     */
    fun getStats(): NodeBuilderStats = statsManager.getStats()

    /**
     * Сбросить счетчики статистики и кэш.
     */
    fun resetStats() {
        statsManager.reset()
        nodeCache.clear()
        log.debug("NodeBuilder stats and cache reset")
    }

    /**
     * Очистить кэш существующих нод (можно вызывать периодически для освобождения памяти).
     */
    fun clearCache() {
        nodeCache.clear()
    }

    private fun toMetaMap(meta: NodeMeta): Map<String, Any> =
        objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>
}
