package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindExtractor
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NodeKindRefinerImplTest {
    @Test
    fun `forType - возвращает base если экстракторов нет`() {
        val refiner = NodeKindRefinerImpl(extractors = emptyList())

        val base = NodeKind.CLASS
        val raw =
            RawType(
                lang = SrcLang.kotlin,
                filePath = "A.kt",
                pkgFqn = "com.example",
                simpleName = "A",
                kindRepr = "class",
                supertypesRepr = emptyList(),
                annotationsRepr = emptyList(),
                span = null,
                text = null,
            )

        assertThat(refiner.forType(base, raw, fileUnit = null)).isEqualTo(base)
    }

    @Test
    fun `forFunction - берет первое refineFunction которое вернуло не-null`() {
        val e1 = mockk<NodeKindExtractor>()
        val e2 = mockk<NodeKindExtractor>()

        val file = RawFileUnit(lang = SrcLang.kotlin, filePath = "A.kt", pkgFqn = null, imports = listOf("x.Y"))

        every { e1.supports(Lang.kotlin) } returns true
        every { e2.supports(Lang.kotlin) } returns true

        val base = NodeKind.METHOD
        val raw =
            RawFunction(
                lang = SrcLang.kotlin,
                filePath = "A.kt",
                pkgFqn = null,
                ownerFqn = null,
                name = "f",
                signatureRepr = "fun f()",
                paramNames = emptyList(),
                annotationsRepr = emptySet(),
                rawUsages = emptyList(),
                throwsRepr = null,
                kdoc = null,
                span = null,
                text = null,
            )

        every { e1.refineFunction(eq(base), eq(raw), any()) } returns null
        every { e2.refineFunction(eq(base), eq(raw), any()) } returns NodeKind.CLIENT

        val kind = NodeKindRefinerImpl(listOf(e1, e2)).forFunction(base, raw, fileUnit = file)
        assertThat(kind).isEqualTo(NodeKind.CLIENT)
    }

    @Test
    fun `forField - игнорирует экстракторы которые не поддерживают язык`() {
        val e1 = mockk<NodeKindExtractor>()
        val e2 = mockk<NodeKindExtractor>()

        val base = NodeKind.FIELD
        val raw =
            RawField(
                lang = SrcLang.kotlin,
                filePath = "A.kt",
                pkgFqn = null,
                ownerFqn = "com.example.A",
                name = "x",
                typeRepr = "Int",
                annotationsRepr = emptyList(),
                kdoc = null,
                span = null,
                text = null,
            )

        every { e1.supports(Lang.kotlin) } returns false
        every { e2.supports(Lang.kotlin) } returns true

        every { e1.refineField(any(), any(), any()) } answers { error("must not be called") }
        every { e2.refineField(eq(base), eq(raw), any()) } answers { thirdArg<NodeKindContext>(); NodeKind.FIELD }

        val kind = NodeKindRefinerImpl(listOf(e1, e2)).forField(base, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.FIELD)
    }
}

