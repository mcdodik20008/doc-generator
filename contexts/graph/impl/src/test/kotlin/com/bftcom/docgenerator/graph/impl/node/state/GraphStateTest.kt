package com.bftcom.docgenerator.graph.impl.node.state

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GraphStateTest {
    private lateinit var state: GraphState
    private val app = Application(id = 1L, key = "app", name = "App")

    @BeforeEach
    fun setUp() {
        state = GraphState()
    }

    @Test
    fun `getPackage returns null when package not found`() {
        val result = state.getPackage("com.example")

        assertThat(result).isNull()
    }

    @Test
    fun `getOrPutPackage returns existing package`() {
        val pkg = createPackageNode("com.example")
        state.getOrPutPackage("com.example") { pkg }

        val result = state.getOrPutPackage("com.example") { createPackageNode("com.other") }

        assertThat(result).isEqualTo(pkg)
    }

    @Test
    fun `getOrPutPackage creates new package when not exists`() {
        val newPkg = createPackageNode("com.example")

        val result = state.getOrPutPackage("com.example") { newPkg }

        assertThat(result).isEqualTo(newPkg)
        assertThat(state.getPackage("com.example")).isEqualTo(newPkg)
    }

    @Test
    fun `getType returns null when type not found`() {
        val result = state.getType("com.example.Type")

        assertThat(result).isNull()
    }

    @Test
    fun `putType stores type`() {
        val type = createTypeNode("com.example.Type")

        state.putType("com.example.Type", type)

        assertThat(state.getType("com.example.Type")).isEqualTo(type)
    }

    @Test
    fun `putType overwrites existing type`() {
        val type1 = createTypeNode("com.example.Type")
        val type2 = createTypeNode("com.example.Type")

        state.putType("com.example.Type", type1)
        state.putType("com.example.Type", type2)

        assertThat(state.getType("com.example.Type")).isEqualTo(type2)
    }

    @Test
    fun `getFunction returns null when function not found`() {
        val result = state.getFunction("com.example.function")

        assertThat(result).isNull()
    }

    @Test
    fun `putFunction stores function`() {
        val func = createFunctionNode("com.example.function")

        state.putFunction("com.example.function", func)

        assertThat(state.getFunction("com.example.function")).isEqualTo(func)
    }

    @Test
    fun `getFilePackage returns null when file not found`() {
        val result = state.getFilePackage("Test.kt")

        assertThat(result).isNull()
    }

    @Test
    fun `setFilePackage stores package`() {
        state.setFilePackage("Test.kt", "com.example")

        assertThat(state.getFilePackage("Test.kt")).isEqualTo("com.example")
    }

    @Test
    fun `getFileImports returns null when file not found`() {
        val result = state.getFileImports("Test.kt")

        assertThat(result).isNull()
    }

    @Test
    fun `setFileImports stores imports`() {
        val imports = listOf("com.example.Type1", "com.example.Type2")

        state.setFileImports("Test.kt", imports)

        assertThat(state.getFileImports("Test.kt")).isEqualTo(imports)
    }

    @Test
    fun `getFileUnit returns null when file not found`() {
        val result = state.getFileUnit("Test.kt")

        assertThat(result).isNull()
    }

    @Test
    fun `putFileUnit stores file unit`() {
        val unit = createRawFileUnit("Test.kt", "com.example")

        state.putFileUnit("Test.kt", unit)

        assertThat(state.getFileUnit("Test.kt")).isEqualTo(unit)
    }

    @Test
    fun `rememberFileUnit stores file unit with package`() {
        val unit = createRawFileUnit("Test.kt", "com.example", listOf("com.other.Type"))

        state.rememberFileUnit(unit)

        assertThat(state.getFileUnit("Test.kt")).isEqualTo(unit)
        assertThat(state.getFilePackage("Test.kt")).isEqualTo("com.example")
        assertThat(state.getFileImports("Test.kt")).isEqualTo(listOf("com.other.Type"))
    }

    @Test
    fun `rememberFileUnit stores file unit without package sets empty string`() {
        val unit = createRawFileUnit("Test.kt", null, listOf("com.other.Type"))

        state.rememberFileUnit(unit)

        assertThat(state.getFileUnit("Test.kt")).isEqualTo(unit)
        assertThat(state.getFilePackage("Test.kt")).isEqualTo("")
        assertThat(state.getFileImports("Test.kt")).isEqualTo(listOf("com.other.Type"))
    }

    @Test
    fun `rememberFileUnit overwrites existing file unit`() {
        val unit1 = createRawFileUnit("Test.kt", "com.example1")
        val unit2 = createRawFileUnit("Test.kt", "com.example2")

        state.rememberFileUnit(unit1)
        state.rememberFileUnit(unit2)

        assertThat(state.getFileUnit("Test.kt")).isEqualTo(unit2)
        assertThat(state.getFilePackage("Test.kt")).isEqualTo("com.example2")
    }

    private fun createPackageNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn,
            kind = NodeKind.PACKAGE,
            lang = Lang.kotlin,
        )
    }

    private fun createTypeNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
    }

    private fun createFunctionNode(fqn: String): Node {
        return Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
    }

    private fun createRawFileUnit(
        filePath: String,
        pkgFqn: String?,
        imports: List<String> = emptyList(),
    ): RawFileUnit {
        return RawFileUnit(
            lang = SrcLang.kotlin,
            filePath = filePath,
            pkgFqn = pkgFqn,
            imports = imports,
        )
    }
}
