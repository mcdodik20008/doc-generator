package com.bftcom.docgenerator.graph.impl.node.builder

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Строитель нод - отвечает за создание и обновление Node сущностей.
 */
class NodeBuilder(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Счетчики для статистики
    private var createdCount = 0
    private var updatedCount = 0
    private var skippedCount = 0

    // Кэш существующих нод для избежания N+1 запросов
    private val existingNodesCache = mutableMapOf<String, Node?>()

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
        // Валидация входных данных
        validateNodeData(fqn, span, parent, sourceCode)

        // Кэшируем запросы к БД для избежания N+1 проблемы
        val existing =
            existingNodesCache.getOrPut(fqn) {
                nodeRepo.findByApplicationIdAndFqn(
                    requireNotNull(application.id) { "Application must have an ID" },
                    fqn,
                )
            }
        val metaMap = toMetaMap(meta)

        // Ограничиваем размер sourceCode для защиты от переполнения
        val normalizedSourceCode =
            sourceCode?.let {
                if (it.length > maxSourceCodeSize) {
                    log.warn(
                        "Source code truncated: fqn={}, originalSize={}, maxSize={}",
                        fqn,
                        it.length,
                        maxSourceCodeSize,
                    )
                    it.take(maxSourceCodeSize) + "\n... [truncated]"
                } else {
                    it
                }
            }

        val lineStart: Int? = span?.first
        var lineEnd: Int? = span?.last
        if (normalizedSourceCode?.isNotEmpty() == true && lineStart != null) {
            lineEnd = lineStart + countLinesNormalized(normalizedSourceCode) - 1
        }

        // Вычисляем хеш исходного кода (используем оригинальный, не обрезанный)
        val codeHash = computeCodeHash(sourceCode)

        return if (existing == null) {
            // Создание новой ноды
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
                existingNodesCache[fqn] = newNode
                createdCount++
                log.trace("Node created: id={}, fqn={}, hash={}", newNode.id, fqn, codeHash?.take(8))
                newNode
            } catch (e: Exception) {
                log.error("Failed to save new node: kind={}, fqn={}, error={}", kind, fqn, e.message, e)
                throw e
            }
        } else {
            // Обновление существующей ноды
            log.debug("Updating existing node: id={}, kind={}, fqn={}", existing.id, kind, fqn)
            updateExistingNode(
                existing,
                name,
                packageName,
                kind,
                lang,
                parent,
                filePath,
                lineStart,
                lineEnd,
                normalizedSourceCode,
                docComment,
                signature,
                codeHash,
                metaMap,
            )
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
        sourceCode: String?,
        docComment: String?,
        signature: String?,
        codeHash: String?,
        metaMap: Map<String, Any>,
    ): Node {
        var changed = false

        fun <T> setIfChanged(
            curr: T,
            new: T,
            apply: (T) -> Unit,
        ) {
            if (curr != new) {
                apply(new)
                changed = true
            }
        }

        // Оптимизация: если codeHash не изменился, код не изменился
        // Можно пропустить обновление sourceCode и связанных полей
        val codeHashChanged = existing.codeHash != codeHash

        setIfChanged(existing.name, name) { existing.name = it }
        setIfChanged(existing.packageName, packageName) { existing.packageName = it }
        setIfChanged(existing.kind, kind) { existing.kind = it }
        setIfChanged(existing.lang, lang) { existing.lang = it }
        setIfChanged(existing.parent?.id, parent?.id) { existing.parent = parent }
        setIfChanged(existing.filePath, filePath) { existing.filePath = it }

        // Обновляем lineStart/lineEnd только если код изменился или они явно указаны
        if (codeHashChanged || lineStart != existing.lineStart || lineEnd != existing.lineEnd) {
            setIfChanged(existing.lineStart, lineStart) { existing.lineStart = it }
            setIfChanged(existing.lineEnd, lineEnd) { existing.lineEnd = it }
        }

        // Обновляем sourceCode только если codeHash изменился
        if (codeHashChanged) {
            setIfChanged(existing.sourceCode, sourceCode) { existing.sourceCode = it }
        }

        setIfChanged(existing.docComment, docComment) { existing.docComment = it }
        setIfChanged(existing.signature, signature) { existing.signature = it }
        setIfChanged(existing.codeHash, codeHash) { existing.codeHash = it }

        @Suppress("UNCHECKED_CAST")
        val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
        val merged =
            (currentMeta + metaMap).filterValues {
                it != null &&
                    when (it) {
                        is Collection<*> -> it.isNotEmpty()
                        is Map<*, *> -> it.isNotEmpty()
                        else -> true
                    }
            }
        setIfChanged(existing.meta, merged) { existing.meta = it as Map<String, Any> }

        return if (changed) {
            log.debug("Node updated: id={}, fqn={}, changes detected", existing.id, existing.fqn)
            try {
                val updated = nodeRepo.save(existing)
                // Обновляем кэш
                existingNodesCache[existing.fqn] = updated
                updatedCount++
                updated
            } catch (e: Exception) {
                log.error("Failed to update node: id={}, fqn={}, error={}", existing.id, existing.fqn, e.message, e)
                throw e
            }
        } else {
            log.trace("Node unchanged: id={}, fqn={}, skipping save", existing.id, existing.fqn)
            skippedCount++
            existing
        }
    }

    /**
     * Оптимизированный подсчет строк с нормализацией окончаний строк.
     */
    private fun countLinesNormalized(src: String): Int {
        if (src.isEmpty()) return 0
        return src.replace("\r\n", "\n").count { it == '\n' } + 1
    }

    /**
     * Вычисляет SHA-256 хеш исходного кода для отслеживания изменений.
     */
    private fun computeCodeHash(sourceCode: String?): String? {
        if (sourceCode.isNullOrBlank()) return null

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(sourceCode.toByteArray(Charsets.UTF_8))
            val hash = hashBytes.joinToString("") { "%02x".format(it) }
            log.trace("Computed code hash: length={}, hash={}", sourceCode.length, hash.take(16))
            hash
        } catch (e: Exception) {
            log.warn("Failed to compute code hash: {}", e.message, e)
            null
        }
    }

    /**
     * Валидация данных ноды перед сохранением.
     */
    private fun validateNodeData(
        fqn: String,
        span: IntRange?,
        parent: Node?,
        sourceCode: String?,
    ) {
        try {
            // Валидация FQN
            require(fqn.isNotBlank()) { "FQN cannot be blank" }
            require(fqn.length <= 1000) { "FQN is too long: ${fqn.length} characters (max 1000)" }

            // Валидация формата FQN (базовая проверка)
            require(fqn.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_.]*$"))) {
                "FQN has invalid format: $fqn (must start with letter/underscore, contain only alphanumeric, dots, underscores)"
            }

            // Валидация диапазона строк
            span?.let {
                require(it.first >= 0) { "lineStart must be non-negative, got ${it.first}" }
                require(it.first <= it.last) {
                    "lineStart (${it.first}) must be <= lineEnd (${it.last})"
                }
            }

            // Валидация parent
            parent?.let {
                require(it.application.id == application.id) {
                    "Parent node (${it.fqn}) must belong to the same application (${application.id})"
                }

                // Проверка на циклические зависимости (базовая)
                require(it.id != null) {
                    "Parent node must be persisted before being used as parent"
                }

                // Проверка, что parent не является самим узлом (защита от самоссылки)
                require(it.fqn != fqn) {
                    "Node cannot be its own parent: fqn=$fqn"
                }
            }

            // Валидация размера sourceCode
            sourceCode?.let {
                if (it.length > maxSourceCodeSize) {
                    log.warn(
                        "Source code too large: fqn={}, size={} bytes (max {}), truncating",
                        fqn,
                        it.length,
                        maxSourceCodeSize,
                    )
                }
            }
        } catch (e: IllegalArgumentException) {
            log.error("Validation failed for node: fqn={}, error={}", fqn, e.message)
            throw e
        }
    }

    /**
     * Получить статистику операций (для логирования на уровне выше).
     */
    fun getStats(): NodeBuilderStats = NodeBuilderStats(createdCount, updatedCount, skippedCount)

    /**
     * Сбросить счетчики статистики и кэш.
     */
    fun resetStats() {
        createdCount = 0
        updatedCount = 0
        skippedCount = 0
        existingNodesCache.clear()
        log.debug("NodeBuilder stats and cache reset")
    }

    /**
     * Очистить кэш существующих нод (можно вызывать периодически для освобождения памяти).
     */
    fun clearCache() {
        val size = existingNodesCache.size
        existingNodesCache.clear()
        log.debug("Cleared node cache: removed {} entries", size)
    }

    private fun toMetaMap(meta: NodeMeta): Map<String, Any> = objectMapper.convertValue(meta, Map::class.java) as Map<String, Any>

    /**
     * Статистика операций NodeBuilder.
     */
    data class NodeBuilderStats(
        val created: Int,
        val updated: Int,
        val skipped: Int,
    ) {
        val total: Int get() = created + updated + skipped
    }
}
