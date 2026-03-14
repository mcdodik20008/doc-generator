package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды EnsurePackageCmd - создает ноду пакета, если её еще нет.
 * Создаёт всю иерархию промежуточных пакетов с правильными parent-child связями.
 */
class EnsurePackageHandler : CommandHandler<EnsurePackageCmd> {
    override fun handle(
        cmd: EnsurePackageCmd,
        state: GraphState,
        builder: NodeBuilder,
    ) {
        ensurePackageChain(
            pkgFqn = cmd.pkgFqn,
            state = state,
            builder = builder,
            filePath = cmd.filePath,
            spanStart = cmd.spanStart,
            spanEnd = cmd.spanEnd,
            sourceText = cmd.sourceText,
        )
    }
}

/**
 * Создаёт полную цепочку пакетов от корневого до листового, устанавливая parent-child связи.
 *
 * Для FQN "com.bftcom.rr.uds.config" создаёт:
 * - com (parent=null)
 * - com.bftcom (parent=com)
 * - com.bftcom.rr (parent=com.bftcom)
 * - com.bftcom.rr.uds (parent=com.bftcom.rr)
 * - com.bftcom.rr.uds.config (parent=com.bftcom.rr.uds)
 *
 * Промежуточные пакеты, уже существующие в state/БД, переиспользуются.
 */
internal fun ensurePackageChain(
    pkgFqn: String,
    state: GraphState,
    builder: NodeBuilder,
    filePath: String?,
    spanStart: Int = 0,
    spanEnd: Int = 1,
    sourceText: String? = null,
): Node {
    val segments = pkgFqn.split('.')
    var parentNode: Node? = null

    for (i in segments.indices) {
        val currentFqn = segments.subList(0, i + 1).joinToString(".")
        val isLeaf = (i == segments.size - 1)
        val currentParent = parentNode

        parentNode = state.getOrPutPackage(currentFqn) {
            builder.upsertNode(
                fqn = currentFqn,
                kind = NodeKind.PACKAGE,
                name = segments[i],
                packageName = currentFqn,
                parent = currentParent,
                lang = Lang.kotlin,
                filePath = if (isLeaf) filePath else null,
                span = if (isLeaf && filePath != null) spanStart..spanEnd else null,
                signature = null,
                sourceCode = if (isLeaf) sourceText else null,
                docComment = null,
                meta = NodeMeta(
                    source = if (isLeaf) "package/fileUnit" else "package/hierarchy",
                    pkgFqn = currentFqn,
                ),
            )
        }
    }

    return parentNode ?: error("Empty package FQN: $pkgFqn")
}
