package com.bftcom.docgenerator.graph.api.model.rawdecl

/**
 * Поле или свойство (property).
 *
 * Представляет член класса, интерфейса или объекта.
 * На этом уровне нет различий между var/val, static/non-static и т.п.
 */
data class RawField(
    override val lang: SrcLang,
    override val filePath: String,
    override val pkgFqn: String?,
    /** FQN владельца (например, класса), если поле принадлежит типу. */
    val ownerFqn: String?,
    /** Имя поля. */
    val name: String,
    /** Тип поля в виде строки, как указан в исходнике. */
    val typeRepr: String?,
    /** Аннотации, применённые к полю (короткие или полные имена). */
    val annotationsRepr: List<String>,
    /** Сырой KDoc комментарий, если присутствует. */
    val kdoc: String?,
    override val span: LineSpan?,
    override val text: String?,
    override val attributes: Map<String, Any?> = emptyMap(),
) : RawDecl
