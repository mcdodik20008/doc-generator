package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для связей вызовов методов (CALLS).
 */
@Component
class CallEdgeLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        // TODO: Нет обработки ошибок - если метаданные некорректны, метод упадет
        // TODO: Нет логирования для отладки проблем с линковкой
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val usages = meta.rawUsages ?: return emptyList()
        // TODO: Если imports null, используется пустой список - может привести к неполной линковке
        val imports = meta.imports ?: emptyList()
        // TODO: Если ownerFqn некорректный, owner будет null - нет логирования этого случая
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
        val pkg = node.packageName.orEmpty()

        // TODO: Линейный поиск по всем usages может быть медленным для методов с множеством вызовов
        usages.forEach { u ->
            when (u) {
                is RawUsage.Simple -> {
                    if (owner != null) {
                        // TODO: Конкатенация строк для FQN может быть неэффективной
                        index.findByFqn("${owner.fqn}.${u.name}")?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                            return@forEach
                        }
                    }
                    // TODO: checkIsCall() использует эвристику - может давать ложные срабатывания
                    // TODO: Нет обработки перегруженных методов - линкуется только по имени, не по сигнатуре
                    if (u.checkIsCall()) {
                        // TODO: resolveType может вернуть неправильный тип при name collision
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                        }
                    }
                }
                is RawUsage.Dot -> {
                    // TODO: Проверка isUpperCase() - слишком упрощенная эвристика для определения типов
                    // TODO: Не учитывает Kotlin conventions (например, companion objects, extension functions)
                    // TODO: firstOrNull() может вернуть null если receiver пустая строка
                    val recvType =
                        if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                            index.resolveType(u.receiver, imports, pkg)
                        } else {
                            owner
                        }
                    // TODO: Если recvType null, связь не создается - нет логирования пропущенных случаев
                    recvType?.let { r ->
                        // TODO: Нет обработки случая когда member не найден - молчаливо пропускается
                        index.findByFqn("${r.fqn}.${u.member}")?.let {
                            res += Triple(node, it, EdgeKind.CALLS)
                        }
                    }
                }
            }
        }
        return res
    }
}

