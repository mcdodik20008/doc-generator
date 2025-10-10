package com.bftcom.docgenerator.domain.nodedoc

// Структуры для JSONB полей
data class TypeDesc(
    val type: String? = null,
    val desc: String? = null
)

typealias ParamsMap   = Map<String, TypeDesc> // {"arg": {"type":"T","desc":"..."}}
typealias ThrowsList  = List<TypeDesc>        // [{"type":"E","desc":"..." }]
typealias QualityMap  = Map<String, Any>      // {"completeness":..,"truthfulness":..,"helpfulness":..}
typealias EvidenceMap = Map<String, Any>
typealias ModelMeta   = Map<String, Any>
