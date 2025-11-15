package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.node.builder.FqnBuilder
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды UpsertFunctionCmd - создает/обновляет ноду функции/метода.
 */
class UpsertFunctionHandler(
    private val nodeKindRefiner: NodeKindRefiner,
) : CommandHandler<UpsertFunctionCmd> {
    override fun handle(cmd: UpsertFunctionCmd, state: GraphState, builder: NodeBuilder) {
        val r = cmd.raw
        val pkgFqn = r.pkgFqn ?: state.getFilePackage(r.filePath)
        val fqn = FqnBuilder.buildFunctionFqn(r.ownerFqn, pkgFqn, r.name)
        
        val parent =
            when {
                !r.ownerFqn.isNullOrBlank() -> state.getType(r.ownerFqn!!)
                !pkgFqn.isNullOrBlank() -> state.getPackage(pkgFqn)
                else -> null
            }
        
        val sig =
            r.signatureRepr ?: buildString {
                append(r.name).append('(').append(r.paramNames.joinToString(",")).append(')')
            }
        
        val fnKind = nodeKindRefiner.forFunction(NodeKind.METHOD, r, state.getFileUnit(r.filePath))
        
        val node = builder.upsertNode(
            fqn = fqn,
            kind = fnKind,
            name = r.name,
            packageName = pkgFqn,
            parent = parent,
            lang = Lang.kotlin,
            filePath = r.filePath,
            span = r.span?.let { it.start..it.end },
            signature = sig,
            sourceCode = r.text,
            docComment = r.kdoc,
            meta =
                NodeMeta(
                    source = "function",
                    pkgFqn = pkgFqn,
                    ownerFqn = r.ownerFqn,
                    params = r.paramNames,
                    rawUsages = r.rawUsages,
                    annotations = r.annotationsRepr.toList(),
                    imports = state.getFileImports(r.filePath),
                    throwsTypes = r.throwsRepr,
                    kdoc = r.kdoc?.let { KDocMeta(summary = it) },
                ),
        )
        
        state.putFunction(fqn, node)
    }
}

