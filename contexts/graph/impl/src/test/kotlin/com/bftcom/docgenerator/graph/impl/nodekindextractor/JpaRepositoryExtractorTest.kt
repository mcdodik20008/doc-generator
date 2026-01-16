package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class JpaRepositoryExtractorTest {
    private val extractor = JpaRepositoryExtractor()

    @Test
    fun `id returns jpa-repository`() {
        assertThat(extractor.id()).isEqualTo("jpa-repository")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @Test
    fun `refineType returns DB_QUERY for Repository annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.stereotype.Repository")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.DB_QUERY)
    }

    @ParameterizedTest
    @CsvSource(
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.data.repository.CrudRepository",
        "org.springframework.data.repository.PagingAndSortingRepository"
    )
    fun `refineType returns DB_QUERY for JPA repository supertypes`(supertype: String) {
        val raw = createRawType(
            supertypesRepr = listOf(supertype)
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.DB_QUERY)
    }

    @ParameterizedTest
    @CsvSource(
        "UserRepository, com.example",
        "OrderDao, com.example"
    )
    fun `refineType returns DB_QUERY for classes ending with Repository or Dao`(className: String, pkg: String) {
        val raw = createRawType(
            simpleName = className,
            pkgFqn = pkg
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.DB_QUERY)
    }

    @Test
    fun `refineType returns DB_QUERY for package containing repository`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.repository"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.DB_QUERY)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example.service"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive matching`() {
        val raw = createRawType(
            simpleName = "userrepository",
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.DB_QUERY)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        annotationsRepr: List<String> = emptyList(),
        supertypesRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "interface",
            supertypesRepr = supertypesRepr,
            annotationsRepr = annotationsRepr,
            span = null,
            text = null,
        )
    }

    private fun createContext(): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = null,
        )
    }
}
