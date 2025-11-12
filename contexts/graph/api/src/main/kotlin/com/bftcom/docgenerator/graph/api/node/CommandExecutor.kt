package com.bftcom.docgenerator.graph.api.node

import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd

interface CommandExecutor {
    fun execute(cmd: DeclCmd)
}
