package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды EnsurePackageCmd - создает ноду пакета, если её еще нет.
 */
class EnsurePackageHandler : CommandHandler<EnsurePackageCmd> {
    override fun handle(
        cmd: EnsurePackageCmd,
        state: GraphState,
        builder: NodeBuilder,
    ) {
        val pkg = cmd.pkgFqn
        state.getOrPutPackage(pkg) {
            builder.upsertNode(
                fqn = pkg,
                kind = NodeKind.PACKAGE,
                name = pkg.substringAfterLast('.'),
                packageName = pkg,
                parent = null,
                lang = Lang.kotlin,
                filePath = cmd.filePath,
                span = cmd.spanStart..cmd.spanEnd,
                signature = null,
                sourceCode = cmd.sourceText,
                docComment = null,
                meta = NodeMeta(source = "package/fileUnit", pkgFqn = pkg),
            )
        }
    }
}
