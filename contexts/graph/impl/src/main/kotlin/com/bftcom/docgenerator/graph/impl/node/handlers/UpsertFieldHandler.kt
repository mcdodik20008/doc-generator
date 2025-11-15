package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.node.builder.FqnBuilder
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды UpsertFieldCmd - создает/обновляет ноду поля/свойства.
 */
class UpsertFieldHandler(
    private val nodeKindRefiner: NodeKindRefiner,
) : CommandHandler<UpsertFieldCmd> {
    override fun handle(cmd: UpsertFieldCmd, state: GraphState, builder: NodeBuilder) {
        val r = cmd.raw
        val pkg = r.pkgFqn ?: state.getFilePackage(r.filePath)
        val parent = r.ownerFqn?.let { state.getType(it) } ?: pkg?.let { state.getPackage(it) }
        val fieldKind = nodeKindRefiner.forField(NodeKind.FIELD, r, state.getFileUnit(r.filePath))
        
        val fqn = FqnBuilder.buildFieldFqn(r.ownerFqn, r.name)
        
        builder.upsertNode(
            fqn = fqn,
            kind = fieldKind,
            name = r.name,
            packageName = pkg,
            parent = parent,
            lang = Lang.kotlin,
            filePath = r.filePath,
            span = r.span?.let { it.start..it.end },
            signature = null,
            sourceCode = r.text,
            docComment = r.kdoc,
            meta =
                NodeMeta(
                    source = "field",
                    pkgFqn = pkg,
                    ownerFqn = r.ownerFqn,
                    kdoc = r.kdoc?.let { KDocMeta(summary = it) },
                    annotations = r.annotationsRepr,
                ),
        )
    }
}

