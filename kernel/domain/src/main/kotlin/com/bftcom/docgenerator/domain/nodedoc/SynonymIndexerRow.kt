package com.bftcom.docgenerator.domain.nodedoc

interface SynonymIndexerRow {
    fun getNodeId(): Long
    fun getDocTech(): String?
}
