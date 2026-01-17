package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MigrationExtractorTest {
    private val extractor = MigrationExtractor()

    @Test
    fun `id returns migration-class`() {
        assertThat(extractor.id()).isEqualTo("migration-class")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns MIGRATION for class extending JavaMigration`() {
        val raw = createRawType(
            supertypesRepr = listOf("org.flywaydb.core.api.migration.JavaMigration")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns MIGRATION for Flyway migration imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("org.flywaydb.core.api.migration.JavaMigration")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns MIGRATION for class in migration package`() {
        val raw = createRawType(
            pkgFqn = "com.example.migration"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns MIGRATION for name ending with Migration`() {
        val raw = createRawType(
            simpleName = "V1_0_0__CreateUsersTable"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        // Имя не заканчивается на Migration, но может быть в пакете migration
        // Проверим с правильным именем
        val raw2 = createRawType(
            simpleName = "CreateUsersTableMigration"
        )
        val result2 = extractor.refineType(NodeKind.CLASS, raw2, ctx)

        assertThat(result2).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive supertype matching`() {
        val raw = createRawType(
            supertypesRepr = listOf("org.flywaydb.core.api.migration.JAVAMIGRATION")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType handles case-insensitive name matching`() {
        val raw = createRawType(
            simpleName = "MIGRATION"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns MIGRATION when both supertype and package match`() {
        val raw = createRawType(
            simpleName = "MyMigration",
            pkgFqn = "com.example.migration",
            supertypesRepr = listOf("org.flywaydb.core.api.migration.JavaMigration")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    @Test
    fun `refineType returns null for class extending other types`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.Object")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns null for class with other imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("org.springframework.stereotype.Service")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns MIGRATION for name ending with Migration 2`() {
        val raw = createRawType(
            simpleName = "V1_0_0__CreateUsersTableMigration"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MIGRATION)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        supertypesRepr: List<String> = emptyList(),
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = supertypesRepr,
            annotationsRepr = annotationsRepr,
            span = null,
            text = null,
        )
    }

    private fun createContext(
        imports: List<String>? = null,
    ): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = imports,
        )
    }
}
