package com.bftcom.docgenerator.library.api

/**
 * Результат построения графа библиотек.
 */
data class LibraryBuildResult(
    /** Количество обработанных библиотек */
    val librariesProcessed: Int,
    /** Количество созданных/обновлённых LibraryNode */
    val nodesCreated: Int,
    /** Количество библиотек, которые уже существовали (пропущены) */
    val librariesSkipped: Int,
    /** Ошибки при обработке (если были) */
    val errors: List<String> = emptyList(),
)
