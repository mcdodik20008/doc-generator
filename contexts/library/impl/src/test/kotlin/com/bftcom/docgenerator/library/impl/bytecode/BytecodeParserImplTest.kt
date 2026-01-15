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
}

