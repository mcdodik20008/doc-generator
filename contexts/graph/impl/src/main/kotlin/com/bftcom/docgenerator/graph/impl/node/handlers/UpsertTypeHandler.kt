package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.apimetadata.util.ApiMetadataSerializer
import com.bftcom.docgenerator.graph.impl.node.builder.FqnBuilder
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды UpsertTypeCmd - создает/обновляет ноду типа (класс, интерфейс, enum и т.д.).
 */
class UpsertTypeHandler(
    private val nodeKindRefiner: NodeKindRefiner,
    private val apiMetadataCollector: ApiMetadataCollector? = null,
) : CommandHandler<UpsertTypeCmd> {
    override fun handle(
        cmd: UpsertTypeCmd,
        state: GraphState,
        builder: NodeBuilder,
    ) {
        val r = cmd.raw
        val pkgFqn = r.pkgFqn ?: state.getFilePackage(r.filePath).orEmpty()
        val fqn = FqnBuilder.buildTypeFqn(pkgFqn.ifBlank { null }, r.simpleName)

        // Обеспечиваем наличие пакета
        val pkgNode =
            if (pkgFqn.isNotBlank()) {
                state.getOrPutPackage(pkgFqn) {
                    builder.upsertNode(
                        fqn = pkgFqn,
                        kind = NodeKind.PACKAGE,
                        name = pkgFqn.substringAfterLast('.'),
                        packageName = pkgFqn,
                        parent = null,
                        lang = Lang.kotlin,
                        filePath = r.filePath,
                        span = null,
                        signature = null,
                        sourceCode = null,
                        docComment = null,
                        meta = NodeMeta(source = "type:pkgAuto", pkgFqn = pkgFqn),
                    )
                }
            } else {
                null
            }

        // Уточняем kind через классификаторы
        val kind = nodeKindRefiner.forType(cmd.baseKind, r, state.getFileUnit(r.filePath))

        // Извлекаем метаданные API для типа (например, basePath из @RequestMapping)
        val ctx =
            NodeKindContext(
                lang = Lang.kotlin,
                file = state.getFileUnit(r.filePath),
                imports = state.getFileImports(r.filePath),
            )
        val apiMetadata = apiMetadataCollector?.extractTypeMetadata(r, ctx)

        // Создаем/обновляем ноду типа
        val node =
            builder.upsertNode(
                fqn = fqn,
                kind = kind,
                name = r.simpleName,
                packageName = pkgFqn.ifBlank { null },
                parent = pkgNode,
                lang = Lang.kotlin,
                filePath = r.filePath,
                span = r.span?.let { it.start..it.end },
                signature = r.attributes["signature"] as? String,
                sourceCode = r.text,
                docComment = null,
                meta =
                    NodeMeta(
                        source = "type",
                        pkgFqn = pkgFqn.ifBlank { null },
                        supertypesSimple = r.supertypesRepr,
                        imports = state.getFileImports(r.filePath),
                        kdoc = null,
                        annotations = r.annotationsRepr,
                        apiMetadata = ApiMetadataSerializer.serialize(apiMetadata),
                    ),
            )

        state.putType(fqn, node)
    }
}
