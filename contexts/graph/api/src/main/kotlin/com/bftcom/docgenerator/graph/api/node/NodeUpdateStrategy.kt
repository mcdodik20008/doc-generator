package com.bftcom.docgenerator.graph.api.node

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node

/**
 * Данные для обновления узла.
 */
data class NodeUpdateData(
    val name: String?,
    val packageName: String?,
    val kind: NodeKind,
    val lang: Lang,
    val parent: Node?,
    val filePath: String?,
    val lineStart: Int?,
    val lineEnd: Int?,
    val sourceCode: String?,
    val docComment: String?,
    val signature: String?,
    val codeHash: String?,
    val meta: Map<String, Any>,
)

/**
 * Стратегия обновления существующего узла.
 * Определяет, какие поля нужно обновить и как это сделать.
 */
interface NodeUpdateStrategy {
    /**
     * Обновляет существующий узел новыми данными.
     * @param existing Существующий узел
     * @param newData Новые данные
     * @return Обновленный узел (может быть тот же объект, если изменений не было)
     */
    fun update(existing: Node, newData: NodeUpdateData): Node

    /**
     * Проверяет, были ли изменения в узле.
     * @param existing Существующий узел
     * @param newData Новые данные
     * @return true, если есть изменения, требующие сохранения
     */
    fun hasChanges(existing: Node, newData: NodeUpdateData): Boolean
}

