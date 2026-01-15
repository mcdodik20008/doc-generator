package com.bftcom.docgenerator.library.impl.bytecode

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HttpBytecodeAnalyzerImplTest {
    @Test
    fun `analyzeJar - находит http kafka camel и строит callGraph`(@TempDir dir: Path) {
        val jar =
            TestJarUtils.writeJar(
                dir.resolve("lib.jar"),
                mapOf(
                    "com/example/TestClient.class" to TestJarUtils.generateTestClientClassBytes(),
                ),
            )

        val analyzer = HttpBytecodeAnalyzerImpl()
        val res = analyzer.analyzeJar(jar)

        assertThat(res.httpCallSites).isNotEmpty
        assertThat(res.httpCallSites.any { it.clientType == "WebClient" }).isTrue()

        assertThat(res.kafkaCallSites).isNotEmpty
        assertThat(res.kafkaCallSites.any { it.topic == "topic1" }).isTrue()

        // Camel эвристика должна схватить "kafka:topic2"
        assertThat(res.camelCallSites.any { it.uri == "kafka:topic2" }).isTrue()

        assertThat(res.callGraph.calls).isNotEmpty()
    }

    @Test
    fun `analyzeJar - non-jar file returns empty result`(@TempDir dir: Path) {
        val file = dir.resolve("x.txt").toFile().apply { writeText("x") }
        val res = HttpBytecodeAnalyzerImpl().analyzeJar(file)
        assertThat(res.httpCallSites).isEmpty()
        assertThat(res.kafkaCallSites).isEmpty()
        assertThat(res.camelCallSites).isEmpty()
        assertThat(res.methodSummaries).isEmpty()
    }
}

