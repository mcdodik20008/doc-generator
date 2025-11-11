package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.graph.api.declhandler.DeclCmd

interface CommandExecutor {
    fun execute(cmd: DeclCmd)
}