package com.bftcom.docgenerator.graph.api.library

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta

/**
 * Компонент для обогащения Node приложения информацией из LibraryNode.
 *
 * При создании Node метода приложения:
 * 1. Проверяет, вызывает ли метод методы библиотек
 * 2. Если да, извлекает информацию об интеграционных точках из LibraryNode
 * 3. Обогащает метаданные Node этой информацией
 */
interface LibraryNodeEnricher {
    /**
     * Обогащает метаданные Node информацией из библиотек.
     *
     * @param node Node метода приложения
     * @param meta Текущие метаданные Node
     * @return Обогащенные метаданные (или исходные, если ничего не найдено)
     */
    fun enrichNodeMeta(
        node: Node,
        meta: NodeMeta,
    ): NodeMeta
}
