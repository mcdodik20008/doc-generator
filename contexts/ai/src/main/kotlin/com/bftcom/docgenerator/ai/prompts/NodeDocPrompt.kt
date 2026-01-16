package com.bftcom.docgenerator.ai.prompts

import com.bftcom.docgenerator.domain.enums.NodeKind

data class NodeDocPrompt(
    val id: String,
    val systemPrompt: String,
)
