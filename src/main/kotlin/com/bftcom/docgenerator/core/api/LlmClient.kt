package com.bftcom.docgenerator.core.api

import com.bftcom.docgenerator.core.model.DepLine
import com.bftcom.docgenerator.core.model.NodeDocDraft
import com.bftcom.docgenerator.core.model.NodeDocPatch
import com.bftcom.docgenerator.core.model.UsageLine

interface LlmClient {
    fun generateNodeDoc(
        nodeId: Long,
        depsCtx: List<DepLine>,
        locale: String = "ru",
    ): NodeDocDraft

    fun generateUsagePatch(
        nodeId: Long,
        usageCtx: List<UsageLine>,
        locale: String = "ru",
    ): NodeDocPatch
}
