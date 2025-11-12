package com.bftcom.docgenerator.graph.api.linker

import com.bftcom.docgenerator.domain.application.Application

interface GraphLinker {
    fun link(application: Application)
}