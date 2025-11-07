package com.bftcom.docgenerator.graph.api.model

data class KDocParsed(
    val raw: String, // сырой текст без /** */ и звёздочек
    val summary: String?, // первый абзац (краткое описание)
    val description: String?, // остальной текст
    val params: Map<String, String>, // @param name text
    val properties: Map<String, String>, // @property name text (важно для классов)
    val returns: String?, // @return text
    val throws: Map<String, String>, // @throws/exception type text
    val seeAlso: List<String>, // @see ссылки/текст
    val since: String?, // @since
    val otherTags: Map<String, List<String>>, // прочие теги (author, sample и т.д.)
)
