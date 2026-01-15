package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.model.KDocParsed
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class KDocFetcherImplTest {
    private val disposable = Disposer.newDisposable()
    private val psiFactory = run {
        val cfg = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val env =
            KotlinCoreEnvironment.createForProduction(
                disposable,
                cfg,
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        KtPsiFactory(env.project, markGenerated = false)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Test
    fun `parseKDoc - возвращает null если комментария нет`() {
        val file = psiFactory.createFile("A.kt", "package com.example\nfun f() = 1\n")
        val decl = file.declarations.filterIsInstance<KtNamedFunction>().first()

        val parsed = KDocFetcherImpl().parseKDoc(decl)
        assertThat(parsed).isNull()
    }

    @Test
    fun `parseKDoc - парсит summary и description из kdoc`() {
        val src =
            """
            package com.example
            /**
             * Summary line
             *
             * Detailed description.
             */
            fun f(x: Int): Int = x
            """.trimIndent()
        val file = psiFactory.createFile("A.kt", src)
        val decl = file.declarations.filterIsInstance<KtNamedFunction>().first()

        val parsed = KDocFetcherImpl().parseKDoc(decl)
        assertThat(parsed).isNotNull
        assertThat(parsed!!.raw).contains("Summary line")
        assertThat(parsed.summary).contains("Summary line")
        assertThat(parsed.description).contains("Detailed description")
    }

    @Test
    fun `toDocString - формирует компактный текст`() {
        val parsed =
            KDocParsed(
                raw = "raw",
                summary = "Summary",
                description = "Details",
                params = mapOf("x" to "value"),
                properties = mapOf("p" to "prop"),
                returns = "result",
                throws = mapOf("IllegalStateException" to "bad state"),
                seeAlso = listOf("Other"),
                since = "1.0",
                otherTags = mapOf("author" to listOf("me")),
            )

        val doc = KDocFetcherImpl().toDocString(parsed)
        assertThat(doc).contains("Summary")
        assertThat(doc).contains("Parameters:")
        assertThat(doc).contains("Returns:")
        assertThat(doc).contains("Throws:")
        assertThat(doc).contains("See also:")
        assertThat(doc).contains("Since:")
        assertThat(doc).contains("@author")
    }
}

