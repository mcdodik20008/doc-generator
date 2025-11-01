package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.application.Application

interface GraphLinker {
    fun link(application: Application)
}