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

class JobWorkerExtractorTest {
    private val extractor = JobWorkerExtractor()

    @Test
    fun `id returns job-worker`() {
        assertThat(extractor.id()).isEqualTo("job-worker")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @ParameterizedTest
    @CsvSource(
        "org.quartz.Job",
        "org.springframework.batch.core.step.tasklet.Tasklet"
    )
    fun `refineType returns JOB for Job or Tasklet supertypes`(supertype: String) {
        val raw = createRawType(supertypesRepr = listOf(supertype))
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.JOB)
    }

    @ParameterizedTest
    @CsvSource(
        "org.springframework.batch.core.configuration.annotation.JobBuilderFactory",
        "org.quartz.Scheduler"
    )
    fun `refineType returns JOB when imports contain Spring Batch or Quartz`(importName: String) {
        val raw = createRawType()
        val ctx = createContext(imports = listOf(importName))

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.JOB)
    }

    @Test
    fun `refineType returns JOB for Scheduled import with job-related name`() {
        val raw = createRawType(simpleName = "PaymentScheduler")
        val ctx = createContext(imports = listOf("org.springframework.scheduling.annotation.Scheduled"))

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.JOB)
    }

    @ParameterizedTest
    @CsvSource(
        "PaymentJob",
        "OrderWorker",
        "EmailTask",
        "ReportScheduler"
    )
    fun `refineType returns JOB for classes ending with Job, Worker, Task, or Scheduler`(className: String) {
        val raw = createRawType(simpleName = className)
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.JOB)
    }

    @Test
    fun `refineType returns JOB for package containing job`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.job"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.JOB)
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
    fun `refineType returns null when Scheduled import without job-related name`() {
        val raw = createRawType(simpleName = "RegularClass")
        val ctx = createContext(imports = listOf("org.springframework.scheduling.annotation.Scheduled"))

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        supertypesRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = supertypesRepr,
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
    }

    private fun createContext(imports: List<String>? = null): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = imports,
        )
    }
}
