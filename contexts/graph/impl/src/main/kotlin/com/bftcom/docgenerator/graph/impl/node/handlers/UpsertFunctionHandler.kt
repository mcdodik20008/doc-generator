package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.KDocMeta
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.library.LibraryNodeEnricher
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.apimetadata.util.ApiMetadataSerializer
import com.bftcom.docgenerator.graph.impl.node.builder.FqnBuilder
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды UpsertFunctionCmd - создает/обновляет ноду функции/метода.
 */
class UpsertFunctionHandler(
    private val nodeKindRefiner: NodeKindRefiner,
    private val apiMetadataCollector: ApiMetadataCollector? = null,
    private val libraryNodeEnricher: LibraryNodeEnricher? = null,
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
        
        // Извлекаем метаданные API (HTTP, GraphQL, gRPC, Message Broker и т.д.)
        val ownerType = r.ownerFqn?.let { state.getType(it) }
        val ctx = NodeKindContext(
            lang = Lang.kotlin,
            file = state.getFileUnit(r.filePath),
            imports = state.getFileImports(r.filePath),
        )
        val apiMetadata = apiMetadataCollector?.extractFunctionMetadata(
            function = r,
            ownerType = ownerType?.let {
                // TODO: нужно получить RawType из ownerType, но пока используем только аннотации
                null // пока null, потом добавим
            },
            ctx = ctx,
        )
        
        val baseMeta = NodeMeta(
            source = "function",
            pkgFqn = pkgFqn,
            ownerFqn = r.ownerFqn,
            params = r.paramNames,
            rawUsages = r.rawUsages,
            annotations = r.annotationsRepr.toList(),
            imports = state.getFileImports(r.filePath),
            throwsTypes = r.throwsRepr,
            kdoc = r.kdoc?.let { KDocMeta(summary = it) },
            apiMetadata = ApiMetadataSerializer.serialize(apiMetadata),
        )
        
        // Обогащаем метаданные информацией из библиотек
        // Пока пропускаем обогащение - оно будет происходить позже при линковке
        // TODO: можно добавить обогащение здесь, если нужно обогащать метаданные Node
        val enrichedMeta = baseMeta
        
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
            meta = enrichedMeta,
        )
        
        state.putFunction(fqn, node)
    }
}

