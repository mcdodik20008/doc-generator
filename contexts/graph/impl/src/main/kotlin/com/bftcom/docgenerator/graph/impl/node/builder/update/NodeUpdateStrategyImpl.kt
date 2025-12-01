package com.bftcom.docgenerator.graph.impl.node.builder.update

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.node.NodeUpdateData
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Реализация стратегии обновления узла.
 * Оптимизирует обновление, пропуская поля, которые не изменились.
 */
@Component
class NodeUpdateStrategyImpl : NodeUpdateStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun update(existing: Node, newData: NodeUpdateData): Node {
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
        val codeHashChanged = existing.codeHash != newData.codeHash

        setIfChanged(existing.name, newData.name) { existing.name = it }
        setIfChanged(existing.packageName, newData.packageName) { existing.packageName = it }
        setIfChanged(existing.kind, newData.kind) { existing.kind = it }
        setIfChanged(existing.lang, newData.lang) { existing.lang = it }
        setIfChanged(existing.parent?.id, newData.parent?.id) { existing.parent = newData.parent }

        setIfChanged(existing.filePath, newData.filePath) { existing.filePath = it }

        // Обновляем lineStart/lineEnd только если код изменился или они явно указаны
        if (codeHashChanged || newData.lineStart != existing.lineStart || newData.lineEnd != existing.lineEnd) {
            setIfChanged(existing.lineStart, newData.lineStart) { existing.lineStart = it }
            setIfChanged(existing.lineEnd, newData.lineEnd) { existing.lineEnd = it }
        }

        // Обновляем sourceCode только если codeHash изменился
        if (codeHashChanged) {
            setIfChanged(existing.sourceCode, newData.sourceCode) { existing.sourceCode = it }
        }

        setIfChanged(existing.docComment, newData.docComment) { existing.docComment = it }
        setIfChanged(existing.signature, newData.signature) { existing.signature = it }
        setIfChanged(existing.codeHash, newData.codeHash) { existing.codeHash = it }

        // Объединяем метаданные
        @Suppress("UNCHECKED_CAST")
        val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
        val merged =
            (currentMeta + newData.meta).filterValues {
                it != null &&
                    when (it) {
                        is Collection<*> -> it.isNotEmpty()
                        is Map<*, *> -> it.isNotEmpty()
                        else -> true
                    }
            }
        setIfChanged(existing.meta, merged) { existing.meta = it as Map<String, Any> }

        if (changed) {
            log.debug("Node update detected changes: id={}, fqn={}", existing.id, existing.fqn)
        } else {
            log.trace("Node unchanged: id={}, fqn={}", existing.id, existing.fqn)
        }

        return existing
    }

    override fun hasChanges(existing: Node, newData: NodeUpdateData): Boolean {
        val codeHashChanged = existing.codeHash != newData.codeHash

        // Проверяем базовые поля
        if (existing.name != newData.name ||
            existing.packageName != newData.packageName ||
            existing.kind != newData.kind ||
            existing.lang != newData.lang ||
            existing.parent?.id != newData.parent?.id ||
            existing.filePath != newData.filePath ||
            existing.docComment != newData.docComment ||
            existing.signature != newData.signature ||
            existing.codeHash != newData.codeHash
        ) {
            return true
        }

        // Проверяем lineStart/lineEnd только если код изменился или они явно указаны
        if (codeHashChanged || newData.lineStart != existing.lineStart || newData.lineEnd != existing.lineEnd) {
            if (newData.lineStart != existing.lineStart || newData.lineEnd != existing.lineEnd) {
                return true
            }
        }

        // Проверяем sourceCode только если codeHash изменился
        if (codeHashChanged && existing.sourceCode != newData.sourceCode) {
            return true
        }

        // Проверяем метаданные - проверяем, есть ли новые ключи или изменились значения
        @Suppress("UNCHECKED_CAST")
        val currentMeta: Map<String, Any?> = (existing.meta as? Map<String, Any?>) ?: emptyMap()
        // Простая проверка: если есть новые ключи или значения изменились
        if (newData.meta.keys != currentMeta.keys) {
            return true
        }
        for ((key, newValue) in newData.meta) {
            if (currentMeta[key] != newValue) {
                return true
            }
        }

        return false
    }
}

