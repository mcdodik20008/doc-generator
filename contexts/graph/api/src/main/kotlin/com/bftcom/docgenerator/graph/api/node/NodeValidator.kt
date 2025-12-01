package com.bftcom.docgenerator.graph.api.node

import com.bftcom.docgenerator.domain.node.Node

/**
 * Валидатор данных узла перед сохранением.
 */
interface NodeValidator {
    /**
     * Валидирует данные узла.
     * @param fqn Полное имя узла
     * @param span Диапазон строк (опционально)
     * @param parent Родительский узел (опционально)
     * @param sourceCode Исходный код (опционально)
     * @param applicationId ID приложения для проверки принадлежности parent
     * @throws IllegalArgumentException если данные невалидны
     */
    fun validate(
        fqn: String,
        span: IntRange?,
        parent: Node?,
        sourceCode: String?,
        applicationId: Long,
    )
}

