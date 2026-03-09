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
import com.bftcom.docgenerator.graph.impl.nodekindextractor.ConfigExtractor
import com.bftcom.docgenerator.graph.impl.nodekindextractor.EndpointClassExtractor
import com.bftcom.docgenerator.graph.impl.nodekindextractor.ExceptionTypeExtractor
import com.bftcom.docgenerator.graph.impl.nodekindextractor.JobWorkerExtractor
import com.bftcom.docgenerator.graph.impl.nodekindextractor.MyBatisMapperExtractor
import com.bftcom.docgenerator.graph.impl.nodekindextractor.TestClassExtractor
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

    @Test
    fun `forType - TestClassExtractor wins over MyBatisMapperExtractor for test in mappers package`() {
        // RawInboxMapperTest in package ...service.mappers should be TEST, not MAPPER.
        // @Order(0) on TestClassExtractor ensures it runs before @Order(10) MapperExtractor.
        val testExtractor = TestClassExtractor()
        val mapperExtractor = MyBatisMapperExtractor()

        // Test first — matches Spring @Order(0) before @Order(10) injection order
        val refiner = NodeKindRefinerImpl(listOf(testExtractor, mapperExtractor))

        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "RawInboxMapperTest.kt",
            pkgFqn = "com.example.service.mappers",
            simpleName = "RawInboxMapperTest",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val file = RawFileUnit(
            lang = SrcLang.kotlin,
            filePath = "RawInboxMapperTest.kt",
            pkgFqn = "com.example.service.mappers",
            imports = listOf("org.junit.jupiter.api.Test"),
        )

        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = file)
        assertThat(kind).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `forType - TestClassExtractor wins over ConfigExtractor for test in config package`() {
        val testExtractor = TestClassExtractor()
        val configExtractor = ConfigExtractor()

        // Test first — matches Spring @Order(0) before @Order(10) injection order
        val refiner = NodeKindRefinerImpl(listOf(testExtractor, configExtractor))

        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "ConfigurationTest.kt",
            pkgFqn = "com.example.config",
            simpleName = "ConfigurationTest",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val file = RawFileUnit(
            lang = SrcLang.kotlin,
            filePath = "ConfigurationTest.kt",
            pkgFqn = "com.example.config",
            imports = listOf("org.junit.jupiter.api.Test"),
        )

        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = file)
        assertThat(kind).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `forFunction - refineFunction with @Test annotation returns TEST`() {
        val refiner = NodeKindRefinerImpl(listOf(TestClassExtractor()))
        val raw = RawFunction(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example",
            ownerFqn = "com.example.SomeTest",
            name = "shouldWork",
            signatureRepr = "fun shouldWork()",
            paramNames = emptyList(),
            annotationsRepr = setOf("org.junit.jupiter.api.Test"),
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = null,
            span = null,
            text = null,
        )
        val kind = refiner.forFunction(NodeKind.METHOD, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `forFunction - refineFunction with @GetMapping returns ENDPOINT`() {
        val refiner = NodeKindRefinerImpl(listOf(EndpointClassExtractor()))
        val raw = RawFunction(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example",
            ownerFqn = "com.example.SomeController",
            name = "getUser",
            signatureRepr = "fun getUser()",
            paramNames = emptyList(),
            annotationsRepr = setOf("org.springframework.web.bind.annotation.GetMapping"),
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = null,
            span = null,
            text = null,
        )
        val kind = refiner.forFunction(NodeKind.METHOD, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.ENDPOINT)
    }

    @Test
    fun `forFunction - refineFunction with @Scheduled returns JOB`() {
        val refiner = NodeKindRefinerImpl(listOf(JobWorkerExtractor()))
        val raw = RawFunction(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example",
            ownerFqn = "com.example.SomeService",
            name = "cleanup",
            signatureRepr = "fun cleanup()",
            paramNames = emptyList(),
            annotationsRepr = setOf("org.springframework.scheduling.annotation.Scheduled"),
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = null,
            span = null,
            text = null,
        )
        val kind = refiner.forFunction(NodeKind.METHOD, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.JOB)
    }

    @Test
    fun `forType - package segment matching - mappers does not match mapper`() {
        val refiner = NodeKindRefinerImpl(listOf(MyBatisMapperExtractor()))
        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example.mappers",
            simpleName = "SomeConverter",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.CLASS)
    }

    @Test
    fun `forType - ImportTask NOT in job package stays CLASS`() {
        val refiner = NodeKindRefinerImpl(listOf(JobWorkerExtractor()))
        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example.dto",
            simpleName = "ImportTask",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.CLASS)
    }

    @Test
    fun `forType - ValidationError without Exception supertype stays CLASS`() {
        val refiner = NodeKindRefinerImpl(listOf(ExceptionTypeExtractor()))
        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example.dto",
            simpleName = "ValidationError",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.CLASS)
    }

    @Test
    fun `forType - Java source file uses correct lang`() {
        val refiner = NodeKindRefinerImpl(listOf(TestClassExtractor()))
        val raw = RawType(
            lang = SrcLang.java,
            filePath = "A.java",
            pkgFqn = "com.example",
            simpleName = "UserServiceTest",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `forType - UserProperties without config package stays CLASS`() {
        val refiner = NodeKindRefinerImpl(listOf(ConfigExtractor()))
        val raw = RawType(
            lang = SrcLang.kotlin,
            filePath = "A.kt",
            pkgFqn = "com.example.dto",
            simpleName = "UserProperties",
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
        val kind = refiner.forType(NodeKind.CLASS, raw, fileUnit = null)
        assertThat(kind).isEqualTo(NodeKind.CLASS)
    }
}

