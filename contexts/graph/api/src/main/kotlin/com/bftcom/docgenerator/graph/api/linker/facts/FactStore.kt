package com.bftcom.docgenerator.graph.api.linker.facts

import com.bftcom.docgenerator.graph.api.linker.model.Fact
import com.bftcom.docgenerator.graph.api.linker.model.FactKind

interface FactStore {
    fun save(facts: Sequence<Fact>)
    fun getAll(): Sequence<Fact>
    fun findByKind(kind: FactKind): Sequence<Fact>
}
