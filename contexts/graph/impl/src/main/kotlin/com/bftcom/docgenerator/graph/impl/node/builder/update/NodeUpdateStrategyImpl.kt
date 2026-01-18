package com.bftcom.docgenerator.graph.impl.node.builder.update

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.node.NodeUpdateData
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NodeUpdateStrategyImpl : NodeUpdateStrategy {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun update(existing: Node, newData: NodeUpdateData): Node {
        val changed = applyChanges(existing, newData, execute = true)

        if (changed) {
            log.debug("Node update detected changes: id={}, fqn={}", existing.id, existing.fqn)
        }
        return existing
    }

    override fun hasChanges(existing: Node, newData: NodeUpdateData): Boolean {
        return applyChanges(existing, newData, execute = false)
    }

    /**
     * Централизованная логика обработки изменений.
     * @param execute если true — применяет изменения к объекту existing, если false — только проверяет наличие.
     */
    private fun applyChanges(existing: Node, newData: NodeUpdateData, execute: Boolean): Boolean {
        var anyChanged = false
        val codeHashChanged = existing.codeHash != newData.codeHash

        // Вспомогательная функция для обновления полей
        fun <T> updateField(current: T, new: T, applier: (T) -> Unit) {
            if (current != new) {
                anyChanged = true
                if (execute) applier(new)
            }
        }

        // Вспомогательная функция для "мягкого" обновления (null в newData игнорируется)
        fun <T> updateFieldIfPresent(current: T, new: T?, applier: (T) -> Unit) {
            if (new != null && current != new) {
                anyChanged = true
                if (execute) applier(new)
            }
        }

        // 1. Базовые поля
        updateField(existing.name, newData.name) { existing.name = it }
        updateField(existing.packageName, newData.packageName) { existing.packageName = it }
        updateField(existing.kind, newData.kind) { existing.kind = it }
        updateField(existing.lang, newData.lang) { existing.lang = it }
        updateField(existing.filePath, newData.filePath) { existing.filePath = it }
        updateField(existing.docComment, newData.docComment) { existing.docComment = it }
        updateField(existing.signature, newData.signature) { existing.signature = it }
        updateField(existing.codeHash, newData.codeHash) { existing.codeHash = it }

        // Обновление родителя
        if (existing.parent?.id != newData.parent?.id) {
            anyChanged = true
            if (execute) existing.parent = newData.parent
        }

        // 2. Координаты (lineStart/lineEnd)
        // Обновляем, если изменился код ИЛИ если пришли новые не-null значения
        if (codeHashChanged) {
            updateField(existing.lineStart, newData.lineStart) { existing.lineStart = it }
            updateField(existing.lineEnd, newData.lineEnd) { existing.lineEnd = it }
        } else {
            updateFieldIfPresent(existing.lineStart, newData.lineStart) { existing.lineStart = it }
            updateFieldIfPresent(existing.lineEnd, newData.lineEnd) { existing.lineEnd = it }
        }

        // 3. Исходный код (только при смене хэша)
        if (codeHashChanged) {
            updateField(existing.sourceCode, newData.sourceCode) { existing.sourceCode = it }
        }

        // 4. Метаданные (Merge)
        val mergedMeta = mergeMetadata(existing.meta, newData.meta)
        if (existing.meta != mergedMeta) {
            anyChanged = true
            if (execute) existing.meta = mergedMeta
        }

        return anyChanged
    }

    private fun mergeMetadata(existingMeta: Map<String, Any>, newMeta: Map<String, Any?>): Map<String, Any> {
        if (newMeta.isEmpty()) return existingMeta

        val merged = existingMeta.toMutableMap()
        newMeta.forEach { (key, value) ->
            if (value == null) {
                merged.remove(key)
            } else if (isValidValue(value)) {
                merged[key] = value
            }
        }
        return merged
    }

    private fun isValidValue(value: Any): Boolean = when (value) {
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }
}
