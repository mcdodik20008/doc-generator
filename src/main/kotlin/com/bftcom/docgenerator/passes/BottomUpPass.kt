package com.bftcom.docgenerator.passes

import com.bftcom.docgenerator.core.api.ContextBuilder
import com.bftcom.docgenerator.core.api.GraphPort
import com.bftcom.docgenerator.core.api.LlmClient
import com.bftcom.docgenerator.core.api.NodeDocPort

class BottomUpPass(
    private val graph: GraphPort,
    private val ctx: ContextBuilder,
    private val llm: LlmClient,
    private val docs: NodeDocPort,
    private val locale: String = "ru",
    private val modelName: String = "test-llm",
) {
    fun run() {
        for (n in graph.topoOrder()) {
            val deps = graph.dependenciesOf(n)
            val depsCtx = ctx.depsContext(deps, locale)
            val draft = llm.generateNodeDoc(n, depsCtx, locale)
            docs.upsert(n, draft, sourceKind = "llm-bottom-up", modelName = modelName)
        }
    }
}
