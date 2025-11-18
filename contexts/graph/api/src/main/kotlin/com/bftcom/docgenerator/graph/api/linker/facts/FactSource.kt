package com.bftcom.docgenerator.graph.api.linker.facts

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.linker.model.Fact

interface FactSource {
    fun supports(application: Application): Boolean

    fun extractFacts(application: Application): Sequence<Fact>
}
