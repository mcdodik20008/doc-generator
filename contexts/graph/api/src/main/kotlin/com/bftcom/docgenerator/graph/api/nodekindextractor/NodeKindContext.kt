package com.bftcom.docgenerator.graph.api.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit

/**
 * Контекст для правила: кусочек сведений из файла.
 * Без обязательной схемы — только то, что реально уже есть в Raw*.
 */
data class NodeKindContext(
    val lang: Lang,
    val file: RawFileUnit?,
    val imports: List<String>?,
)
