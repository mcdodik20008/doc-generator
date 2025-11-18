package com.bftcom.docgenerator.graph.impl.node.state

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit

/**
 * Состояние сборки графа в рамках одной транзакции.
 * Кэширует созданные ноды для быстрого доступа по FQN.
 */
class GraphState {
    private val packageByFqn = mutableMapOf<String, Node>()
    private val typeByFqn = mutableMapOf<String, Node>()
    private val funcByFqn = mutableMapOf<String, Node>()
    private val filePkg = mutableMapOf<String, String>()
    private val fileImports = mutableMapOf<String, List<String>>()
    private val fileUnitByPath = mutableMapOf<String, RawFileUnit>()

    fun getPackage(fqn: String): Node? = packageByFqn[fqn]

    fun getOrPutPackage(
        fqn: String,
        factory: () -> Node,
    ): Node = packageByFqn.getOrPut(fqn, factory)

    fun getType(fqn: String): Node? = typeByFqn[fqn]

    fun putType(
        fqn: String,
        node: Node,
    ) {
        typeByFqn[fqn] = node
    }

    fun getFunction(fqn: String): Node? = funcByFqn[fqn]

    fun putFunction(
        fqn: String,
        node: Node,
    ) {
        funcByFqn[fqn] = node
    }

    fun getFilePackage(filePath: String): String? = filePkg[filePath]

    fun setFilePackage(
        filePath: String,
        pkg: String,
    ) {
        filePkg[filePath] = pkg
    }

    fun getFileImports(filePath: String): List<String>? = fileImports[filePath]

    fun setFileImports(
        filePath: String,
        imports: List<String>,
    ) {
        fileImports[filePath] = imports
    }

    fun getFileUnit(filePath: String): RawFileUnit? = fileUnitByPath[filePath]

    fun putFileUnit(
        filePath: String,
        unit: RawFileUnit,
    ) {
        fileUnitByPath[filePath] = unit
    }

    fun rememberFileUnit(unit: RawFileUnit) {
        fileUnitByPath[unit.filePath] = unit
        if (unit.pkgFqn != null) {
            filePkg[unit.filePath] = unit.pkgFqn!!
            fileImports[unit.filePath] = unit.imports
        } else {
            filePkg[unit.filePath] = ""
            fileImports[unit.filePath] = unit.imports
        }
    }
}
