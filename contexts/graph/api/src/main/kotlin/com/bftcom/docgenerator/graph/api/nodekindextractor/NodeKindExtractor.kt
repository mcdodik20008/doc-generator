package com.bftcom.docgenerator.graph.api.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType

interface NodeKindExtractor {
    /** Уникальный идентификатор правила (для логов, отладки, сортировки). */
    fun id(): String

    /** Флаг, чтобы легко отключать/фильтровать правила. */
    fun supports(lang: Lang): Boolean = true

    /** Уточнение типа (class/interface/enum/record/object). Возвращи null, если не применимо. */
    fun refineType(base: NodeKind, raw: RawType, ctx: NodeKindContext): NodeKind? = null

    /** Уточнение функции/метода. Возвращи null, если не применимо. */
    fun refineFunction(base: NodeKind, raw: RawFunction, ctx: NodeKindContext): NodeKind? = null

    /** Уточнение поля/свойства. Возвращи null, если не применимо. */
    fun refineField(base: NodeKind, raw: RawField, ctx: NodeKindContext): NodeKind? = null
}