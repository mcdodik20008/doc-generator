package com.bftcom.docgenerator.graph.api.model.rawdecl

import com.bftcom.docgenerator.domain.node.RawUsage

/**
 * Функция или метод (член класса или top-level функция).
 *
 * Не содержит типы параметров — только имена, сигнатура и сырой текст тела.
 * Все вызовы внутри тела представлены как список [com.bftcom.docgenerator.domain.node.RawUsage].
 */
data class RawFunction(
    override val lang: SrcLang,
    override val filePath: String,
    override val pkgFqn: String?,
    /** FQN владельца, если функция является методом класса; null для top-level. */
    val ownerFqn: String?,
    /** Имя функции. */
    val name: String,
    /** Сырой фрагмент сигнатуры функции «до тела» (без нормализации). */
    val signatureRepr: String?,
    /** Имена параметров, как указаны в исходнике. */
    val paramNames: List<String>,
    /** Аннотации, применённые к функции. */
    val annotationsRepr: Set<String>,
    /** Список "сырых" упоминаний/вызовов, найденных в теле функции. */
    val rawUsages: List<RawUsage>,
    /** Список типов исключений, которые выбрасываются из функции. */
    val throwsRepr: List<String>?,
    /** Сырой KDoc комментарий, если присутствует. */
    val kdoc: String?,
    override val span: LineSpan?,
    override val text: String?,
    override val attributes: Map<String, Any?> = emptyMap(),
) : RawDecl
