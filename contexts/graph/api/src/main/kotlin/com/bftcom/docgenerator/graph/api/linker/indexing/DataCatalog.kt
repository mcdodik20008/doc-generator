package com.bftcom.docgenerator.graph.api.linker.indexing

import com.bftcom.docgenerator.graph.api.linker.model.DbTable

interface DataCatalog {
    fun resolveTable(identifier: String): DbTable?
    fun listTables(): Sequence<DbTable>
}
