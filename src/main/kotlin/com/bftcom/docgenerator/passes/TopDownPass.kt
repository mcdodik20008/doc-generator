package com.bftcom.docgenerator.passes

import com.bftcom.docgenerator.core.api.ContextBuilder
import com.bftcom.docgenerator.core.api.GraphPort
import com.bftcom.docgenerator.core.api.LlmClient
import com.bftcom.docgenerator.core.api.NodeDocPort

class TopDownPass(
    private val graph: GraphPort,
    private val ctx: ContextBuilder,
    private val llm: LlmClient,
    private val docs: NodeDocPort,
    private val locale: String = "ru",
    private val modelName: String = "test-llm",
) {
    fun run() {
        for (n in graph.reverseTopoOrder()) {
            val users = graph.dependentsOf(n)
            val usageCtx = ctx.usageContext(users, locale)
            val patch = llm.generateUsagePatch(n, usageCtx, locale)
            docs.merge(n, patch, sourceKind = "llm-top-down", modelName = modelName)
        }
    }
}
