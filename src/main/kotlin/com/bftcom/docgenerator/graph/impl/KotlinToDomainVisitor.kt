package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.repo.ChunkRepository
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * Проекция исходников Kotlin в доменную модель графа.
 *
 * Отвечает за:
 * - **Upsert** узлов [Node] и рёбер [Edge] на основе событий [SourceVisitor].
 * - Создание кодовых чанков [Chunk] по диапазонам строк.
 * - Временные связи по **простым именам** (supertypes, calls) до этапа резолва FQN.
 *
 * Инварианты:
 * - Узел уникален по `(application_id, fqn)`; метод [upsertNode] обеспечивает идемпотентность.
 * - Для top-level функций родитель — PACKAGE-узел текущего файла (кеш `filePackageCache`).
 * - Для member-сущностей родитель — тип (кеш `typeCache`).
 *
 * Ограничения:
 * - **Нет резолва FQN**: `supertypesSimple` и `callsSimple` содержат только идентификаторы.
 * - Возможны временные узлы-заглушки (класс/метод по простому имени).
 *
 * Эволюция:
 * - После внедрения резолва: миграция переносит ребра с временных узлов на реальные FQN и удаляет заглушки.
 *
 * Безопасность/надёжность:
 * - Сохранение рёбер обёрнуто в try/catch для игнора дублей (до появления уникальных индексов/ON CONFLICT).
 * - Чтение исходников для чанков — fail-soft: при ошибке чанк опускается.
 */
class KotlinToDomainVisitor(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val chunkRepo: ChunkRepository,
) : SourceVisitor {

    // Кэши по FQN/пакету
    private val packageByFqn = mutableMapOf<String, Node>()   // "foo" -> PACKAGE
    private val typeByFqn    = mutableMapOf<String, Node>()   // "foo.A" -> TYPE
    private val funcByFqn    = mutableMapOf<String, Node>()   // "foo.baz" / "foo.A.bar" -> METHOD
    private val filePkg      = mutableMapOf<String, String>() // filePath -> "foo"

    private fun upsertNode(
        fqn: String,
        kind: NodeKind,
        name: String?,
        packageName: String?,
        parent: Node?,
        lang: Lang,
        filePath: String?,
        span: IntRange?,
        signature: String? = null,
        sourceCode: String? = null,
        docComment: String? = null,
    ): Node {
        nodeRepo.findByApplicationIdAndFqn(application.id!!, fqn)?.let { return it }
        val n = Node(
            id = null,
            application = application,
            fqn = fqn,
            name = name,
            packageName = packageName,
            kind = kind,
            lang = lang,
            parent = parent,
            filePath = filePath,
            lineStart = span?.first,
            lineEnd = span?.last,
            sourceCode = sourceCode,
            docComment = docComment,
            signature = signature,
            codeHash = null,
            meta = emptyMap(),
        )
        return nodeRepo.save(n)
    }

    private fun functionFqn(pkgFqn: String?, ownerFqn: String?, name: String): String =
        when {
            !ownerFqn.isNullOrBlank() -> "$ownerFqn.$name"
            !pkgFqn.isNullOrBlank()   -> "$pkgFqn.$name"
            else -> name
        }

    override fun onPackage(pkgFqn: String, filePath: String) {
        filePkg[filePath] = pkgFqn
        packageByFqn.getOrPut(pkgFqn) {
            upsertNode(
                fqn = pkgFqn,
                kind = NodeKind.PACKAGE,
                name = pkgFqn.substringAfterLast('.'),
                packageName = pkgFqn,
                parent = null,
                lang = Lang.kotlin,
                filePath = filePath,
                span = 1..1
            )
        }
    }

    override fun onType(
        kind: NodeKind,
        fqn: String,
        pkgFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
        supertypesSimple: List<String>,
    ) {
        val pkgNode = packageByFqn.getOrPut(pkgFqn) {
            upsertNode(pkgFqn, NodeKind.PACKAGE, pkgFqn.substringAfterLast('.'), pkgFqn, null, Lang.kotlin, filePath, 1..1)
        }
        val typeNode = upsertNode(
            fqn = fqn,
            kind = kind,
            name = name,
            packageName = pkgFqn,
            parent = pkgNode,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
        )
        typeByFqn[fqn] = typeNode
    }

    override fun onField(
        ownerFqn: String,
        name: String,
        filePath: String,
        spanLines: IntRange,
    ) {
        val pkg = filePkg[filePath]
        val parent = typeByFqn[ownerFqn] ?: packageByFqn[pkg] // на всякий
        upsertNode(
            fqn = "$ownerFqn.$name",
            kind = NodeKind.FIELD,
            name = name,
            packageName = pkg,
            parent = parent,
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
        )
    }

    override fun onFunction(
        ownerFqn: String?,
        name: String,
        paramNames: List<String>,
        filePath: String,
        spanLines: IntRange,
        callsSimple: List<String>,
    ) {
        val pkgFqn = filePkg[filePath]
        val fqn = functionFqn(pkgFqn, ownerFqn, name) // без скобок
        val fnNode = upsertNode(
            fqn = fqn,
            kind = NodeKind.METHOD,
            name = name,
            packageName = pkgFqn,
            parent = when {
                ownerFqn != null -> typeByFqn[ownerFqn]
                pkgFqn != null   -> packageByFqn[pkgFqn]
                else             -> null
            },
            lang = Lang.kotlin,
            filePath = filePath,
            span = spanLines,
            signature = buildString { append(name); append('('); append(paramNames.joinToString(",")); append(')') },
        )
        funcByFqn[fqn] = fnNode

        for (token in callsSimple) {
            val candidates = when {
                '.' in token -> {
                    // формат "TypeSimple.method" или "foo.B.ping"
                    val (lhs, rhs) = token.split('.', limit = 2)
                    // если уже fqdn — пробуем как есть, иначе — префиксуем текущим пакетом
                    if (lhs.contains('.')) listOf("$lhs.$rhs")
                    else listOfNotNull(pkgFqn?.let { "$it.$lhs.$rhs" })
                }
                else -> {
                    // как раньше: текущий пакет, владелец, голое имя
                    buildList {
                        if (!pkgFqn.isNullOrBlank()) add("$pkgFqn.$token")
                        if (!ownerFqn.isNullOrBlank()) add("$ownerFqn.$token")
                        add(token)
                    }
                }
            }

            val dst = candidates
                .asSequence()
                .mapNotNull { cfqn -> funcByFqn[cfqn] ?: nodeRepo.findByApplicationIdAndFqn(application.id!!, cfqn) }
                .firstOrNull()
                ?: continue

            edgeRepo.save(Edge(src = fnNode, dst = dst, kind = EdgeKind.CALLS))
        }
    }
}

