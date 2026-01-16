package com.bftcom.docgenerator.ai.prompts

import com.bftcom.docgenerator.domain.enums.NodeKind

data class NodeDocContextProfile(
    val kind: NodeKind,
    val hasKdoc: Boolean,
    val hasCode: Boolean,
    val depsCount: Int,
    val childrenCount: Int,
)