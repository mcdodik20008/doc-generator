package com.bftcom.docgenerator.graph.api.linker.model

/**
 * Модель таблицы или представления в БД,
 * на которую могут ссылаться узлы или рёбра графа.
 */
data class DbTable(
    val schema: String?,
    val name: String,
    val type: TableType = TableType.TABLE,
    val columns: List<String> = emptyList(),
    val comment: String? = null
)
