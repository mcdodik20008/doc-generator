package com.bftcom.docgenerator.graph.api.model.rawdecl

/**
 * Пакетная декларация (явное объявление пакета в начале файла).
 */
data class RawPackage(
    override val lang: SrcLang,
    override val filePath: String,
    /** Имя пакета в исходнике (может отличаться от pkgFqn, если неполное). */
    val name: String,
    override val pkgFqn: String?,
    override val span: LineSpan?,
    override val text: String?,
    override val attributes: Map<String, Any?> = emptyMap(),
) : RawDecl
