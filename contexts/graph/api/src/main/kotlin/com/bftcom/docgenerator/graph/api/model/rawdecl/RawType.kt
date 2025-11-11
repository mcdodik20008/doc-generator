package com.bftcom.docgenerator.graph.api.model.rawdecl

/**
 * Тип (class / interface / enum / record / object / и т.п.).
 *
 * Здесь нет нормализации — только «как в коде».
 */
data class RawType(
    override val lang: SrcLang,
    override val filePath: String,
    override val pkgFqn: String?,
    /** Простое имя типа без пакета. */
    val simpleName: String,
    /** Текстовое представление типа: "class", "interface", "enum", "object", "record" и т.д. */
    val kindRepr: String,
    /** Список супертипов (без разрешения FQN, как в исходнике). */
    val supertypesRepr: List<String>,
    /** Список аннотаций (короткие или полные имена). */
    val annotationsRepr: List<String>,
    override val span: LineSpan?,
    override val text: String?,
    override val attributes: Map<String, Any?> = emptyMap(),
) : RawDecl
