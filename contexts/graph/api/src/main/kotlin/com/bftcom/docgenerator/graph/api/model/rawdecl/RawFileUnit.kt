package com.bftcom.docgenerator.graph.api.model.rawdecl

/**
 * Контекст исходного файла.
 *
 * Используется для обозначения единицы компиляции (файл .kt / .java),
 * с пакетом и списком импортов.
 */
data class RawFileUnit(
    override val lang: SrcLang,
    override val filePath: String,
    override val pkgFqn: String?,
    /** Все импортированные FQN из директив `import`. */
    val imports: List<String>,
    override val span: LineSpan? = null,
    override val text: String? = null,
    override val attributes: Map<String, Any?> = emptyMap(),
) : RawDecl
