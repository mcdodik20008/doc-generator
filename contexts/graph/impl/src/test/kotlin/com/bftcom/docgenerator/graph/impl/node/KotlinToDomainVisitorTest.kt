package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.DeclPlanner
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.CommandExecutor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class KotlinToDomainVisitorTest {
    @Test
    fun `onDecl - делегирует планеру и выполняет команды`() {
        val exec = mockk<CommandExecutor>(relaxed = true)
        val planner =
            object : DeclPlanner<RawFileUnit> {
                override val target: KClass<RawFileUnit> = RawFileUnit::class

                override fun plan(raw: RawFileUnit) =
                    listOf(
                        RememberFileUnitCmd(raw),
                        EnsurePackageCmd(pkgFqn = "com.example", filePath = raw.filePath),
                    )
            }

        val visitor = KotlinToDomainVisitor(exec, listOf(planner))
        val raw = RawFileUnit(lang = SrcLang.kotlin, filePath = "A.kt", pkgFqn = "com.example", imports = emptyList())

        visitor.onDecl(raw)

        verify { exec.execute(RememberFileUnitCmd(raw)) }
        verify { exec.execute(EnsurePackageCmd("com.example", "A.kt")) }
    }

    @Test
    fun `onDecl - игнорирует типы без планера`() {
        val exec = mockk<CommandExecutor>(relaxed = true)
        val planner =
            object : DeclPlanner<RawFileUnit> {
                override val target: KClass<RawFileUnit> = RawFileUnit::class

                override fun plan(raw: RawFileUnit) = emptyList<DeclCmd>()
            }
        val visitor = KotlinToDomainVisitor(exec, listOf(planner))
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
                attributes = emptyMap(),
            )

        visitor.onDecl(raw)

        verify(exactly = 0) { exec.execute(any()) }
    }
}
