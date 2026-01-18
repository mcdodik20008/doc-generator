package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import org.springframework.stereotype.Component

/**
 * Линкер для зависимостей из сигнатуры метода (DEPENDS_ON).
 * Извлекает типы из параметров и возвращаемого типа.
 */
@Component
class SignatureDependencyLinker : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        // Определяем токены
        val tokens: Set<String> = when {
            // Если метаданные уже распарсили типы (например, через PSI), используем их
            !meta.paramTypes.isNullOrEmpty() || !meta.returnType.isNullOrBlank() ->
                (meta.paramTypes.orEmpty() + listOfNotNull(meta.returnType))
                    .flatMap { extractAllTypes(it) } // Даже в готовых строках могут быть генерики
                    .toSet()

            // Если есть только сырая сигнатура
            !node.signature.isNullOrBlank() ->
                extractAllTypesFromSignature(node.signature!!)

            else -> emptySet()
        }

        val ownerFqn = meta.ownerFqn
        val src = ownerFqn?.let { index.findByFqn(it) } ?: node

        for (t in tokens) {
            val typeNode = index.resolveType(t, imports, pkg) ?: continue
            // Исключаем связь на самого себя
            if (typeNode.id != src.id) {
                res += Triple(src, typeNode, EdgeKind.DEPENDS_ON)
            }
        }
        return res.distinct()
    }

    private fun extractAllTypesFromSignature(signature: String): Set<String> {
        // 1. Отсекаем часть с параметрами и возвращаемым типом (убираем 'fun name')
        val content = signature.substringAfter('(').substringBeforeLast(')') +
                ":" + signature.substringAfterLast(')')

        return extractAllTypes(content)
    }

    private fun extractAllTypes(text: String): Set<String> {
        // Ищем все слова, которые могут быть именами классов (включая точки для FQN)
        // Игнорируем ключевые слова и цифры.
        return IDENTIFIER_PATTERN.findAll(text)
            .map { it.value }
            .filter { it !in KOTLIN_KEYWORDS }
            // Дополнительная эвристика: типы обычно начинаются с большой буквы
            // или содержат точки (пакеты)
            .filter { it.contains('.') || (it.isNotEmpty() && it[0].isUpperCase()) }
            .toSet()
    }

    companion object {
        private val IDENTIFIER_PATTERN = Regex("""[A-Za-z_][A-Za-z0-9_\.]*""")
        private val KOTLIN_KEYWORDS = setOf(
            "fun", "val", "var", "override", "public", "private",
            "protected", "internal", "class", "interface", "object", "where"
        )
    }
}

