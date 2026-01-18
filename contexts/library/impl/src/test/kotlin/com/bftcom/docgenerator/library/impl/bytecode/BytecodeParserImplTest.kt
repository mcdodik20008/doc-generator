package com.bftcom.docgenerator.library.impl.bytecode

import com.bftcom.docgenerator.domain.enums.NodeKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class BytecodeParserImplTest {
    @Test
    fun `parseJar - извлекает хотя бы class field method`(@TempDir dir: Path) {
        val jar =
            TestJarUtils.writeJar(
                dir.resolve("lib.jar"),
                mapOf(
                    "com/example/TestClient.class" to TestJarUtils.generateTestClientClassBytes(),
                ),
            )

        val parser = BytecodeParserImpl()
        val nodes = parser.parseJar(jar)

        assertThat(nodes).isNotEmpty
        assertThat(nodes.map { it.kind }).contains(NodeKind.CLASS, NodeKind.FIELD, NodeKind.METHOD)
        assertThat(nodes.any { it.fqn == "com.example.TestClient" }).isTrue()
        assertThat(nodes.any { it.fqn == "com.example.TestClient.call" && it.kind == NodeKind.METHOD }).isTrue()
    }

    @Test
    fun `parseJar - non-jar file returns empty`(@TempDir dir: Path) {
        val file = dir.resolve("x.txt").toFile().apply { writeText("x") }
        val nodes = BytecodeParserImpl().parseJar(file)
        assertThat(nodes).isEmpty()
    }

    @Test
    fun `parseJar - empty jar returns empty`(@TempDir dir: Path) {
        val jar = TestJarUtils.emptyJar(dir.resolve("empty.jar"))
        val nodes = BytecodeParserImpl().parseJar(jar)
        assertThat(nodes).isEmpty()
    }

    @Test
    fun `parseJar - извлекает suspend и synthetic признаки`(@TempDir dir: Path) {
        val jar =
            TestJarUtils.writeJar(
                dir.resolve("lib2.jar"),
                mapOf(
                    "com/example/SuspendHelpers.class" to TestJarUtils.generateSuspendHelperClassBytes(),
                    "com/example/Foo\$SuspendLambda.class" to TestJarUtils.generateStateMachineClassBytes(),
                    "com/example/MyInterface.class" to TestJarUtils.generateInterfaceClassBytes(),
                    "com/example/MyEnum.class" to TestJarUtils.generateEnumClassBytes(),
                ),
            )

        val nodes = BytecodeParserImpl().parseJar(jar)

        val suspendMethod =
            nodes.first { it.fqn == "com.example.SuspendHelpers.suspendFun" }
        assertThat(suspendMethod.meta).containsEntry("kotlin_suspend", true)

        val helperMethod =
            nodes.first { it.fqn == "com.example.SuspendHelpers.helper\$default" }
        assertThat(helperMethod.meta).containsEntry("synthetic_coroutine_helper", true)
        assertThat(helperMethod.meta).containsEntry("synthetic", true)

        val bridgeMethod =
            nodes.first { it.fqn == "com.example.SuspendHelpers.bridgeMethod" }
        assertThat(bridgeMethod.meta).containsEntry("bridge", true)

        val syntheticField =
            nodes.first { it.fqn == "com.example.SuspendHelpers.syntheticField" }
        assertThat(syntheticField.meta).containsEntry("synthetic", true)

        val stateMachine =
            nodes.first { it.fqn == "com.example.Foo\$SuspendLambda" }
        assertThat(stateMachine.meta).containsEntry("synthetic_coroutine_class", true)

        val iface =
            nodes.first { it.fqn == "com.example.MyInterface" }
        assertThat(iface.kind).isEqualTo(NodeKind.INTERFACE)

        val enumType =
            nodes.first { it.fqn == "com.example.MyEnum" }
        assertThat(enumType.kind).isEqualTo(NodeKind.ENUM)
    }
}

