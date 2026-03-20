package com.bftcom.docgenerator.graph.impl.config

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.file.Files
import java.nio.file.Path

class YamlConfigScannerTest {
    private val app = Application(id = 1L, key = "test-app", name = "Test App")
    private lateinit var nodeRepo: NodeRepository
    private lateinit var scanner: YamlConfigScanner

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        nodeRepo = mock(NodeRepository::class.java)
        scanner = YamlConfigScanner(nodeRepo)

        // По умолчанию: ничего не найдено в БД, save возвращает переданный объект
        `when`(nodeRepo.findByApplicationIdAndFqn(any(), any())).thenReturn(null)
        `when`(nodeRepo.save(any<Node>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `parses flat YAML and detects HTTP integration`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            rr:
              ups-client:
                api-url: ${'$'}{UPS_SERVICE_API_URL:https://k8s.supercode.ru:3300/bpm/user-profile-service}
                timeout: 5000
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(1)
        verify(nodeRepo).save(argThat<Node> { node ->
            node.kind == NodeKind.INFRASTRUCTURE &&
                node.fqn == "infra:yaml:http:rr.ups-client" &&
                (node.meta["integrationType"] as String) == "HTTP" &&
                (node.meta["defaultUrl"] as String) == "https://k8s.supercode.ru:3300/bpm/user-profile-service"
        })
    }

    @Test
    fun `detects database integration from spring datasource`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            spring:
              datasource:
                url: jdbc:postgresql://localhost:5432/mydb
                username: user
                password: pass
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(1)
        verify(nodeRepo).save(argThat<Node> { node ->
            node.kind == NodeKind.INFRASTRUCTURE &&
                node.fqn == "infra:yaml:database:spring.datasource" &&
                (node.meta["integrationType"] as String) == "DATABASE"
        })
    }

    @Test
    fun `detects Kafka integration`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            spring:
              kafka:
                bootstrap-servers: ${'$'}{KAFKA_SERVERS:localhost:9092}
                consumer:
                  group-id: my-group
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(1)
        verify(nodeRepo).save(argThat<Node> { node ->
            node.fqn == "infra:yaml:kafka:spring.kafka" &&
                (node.meta["integrationType"] as String) == "KAFKA"
        })
    }

    @Test
    fun `detects RabbitMQ integration from host+port+exchange`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            rr:
              users:
                camel-rabbit-consumer-settings:
                  host: ${'$'}{RABBIT_HOST:localhost}
                  port: 5672
                  exchange: users-exchange
                  queue: users-queue
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(1)
        verify(nodeRepo).save(argThat<Node> { node ->
            (node.meta["integrationType"] as String) == "RABBIT"
        })
    }

    @Test
    fun `handles multiple YAML files`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            spring:
              datasource:
                url: jdbc:postgresql://localhost:5432/db
                username: user
        """.trimIndent(),
        )
        writeYaml(
            resourcesDir, "application-dev.yml", """
            rr:
              client:
                api-url: https://dev.example.com/api
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `skips non-integration properties`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            server:
              port: 8080
            logging:
              level:
                root: INFO
            spring:
              jmx:
                enabled: false
        """.trimIndent(),
        )

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(0)
        verify(nodeRepo, never()).save(any<Node>())
    }

    @Test
    fun `does not create duplicate node if already exists`() {
        val resourcesDir = createResourcesDir()
        writeYaml(
            resourcesDir, "application.yml", """
            rr:
              ups-client:
                api-url: https://example.com/api
        """.trimIndent(),
        )

        val existingNode = Node(
            application = app,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
            lang = com.bftcom.docgenerator.domain.enums.Lang.yaml,
        )
        `when`(nodeRepo.findByApplicationIdAndFqn(1L, "infra:yaml:http:rr.ups-client"))
            .thenReturn(existingNode)

        val count = scanner.scan(app, tempDir)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `findYamlFiles finds application and bootstrap yamls`() {
        val resourcesDir = createResourcesDir()
        writeYaml(resourcesDir, "application.yml", "server.port: 8080")
        writeYaml(resourcesDir, "application-prod.yml", "server.port: 80")
        writeYaml(resourcesDir, "bootstrap.yml", "spring.cloud.config.uri: http://config")
        writeYaml(resourcesDir, "other-config.yml", "some.key: value")

        val found = scanner.findYamlFiles(tempDir)

        assertThat(found.map { it.fileName.toString() }).containsExactlyInAnyOrder(
            "application.yml",
            "application-prod.yml",
            "bootstrap.yml",
        )
    }

    @Test
    fun `parseYamlFile flattens nested structure`() {
        val resourcesDir = createResourcesDir()
        val yamlFile = writeYaml(
            resourcesDir, "application.yml", """
            spring:
              datasource:
                url: jdbc:postgresql://localhost:5432/db
              kafka:
                bootstrap-servers: localhost:9092
        """.trimIndent(),
        )

        val props = scanner.parseYamlFile(yamlFile)

        assertThat(props).containsEntry("spring.datasource.url", "jdbc:postgresql://localhost:5432/db")
        assertThat(props).containsEntry("spring.kafka.bootstrap-servers", "localhost:9092")
    }

    @Test
    fun `returns zero when no yaml files found`() {
        val count = scanner.scan(app, tempDir)
        assertThat(count).isEqualTo(0)
    }

    // --- Helpers ---

    private fun createResourcesDir(): Path {
        val resourcesDir = tempDir.resolve("src/main/resources")
        Files.createDirectories(resourcesDir)
        return resourcesDir
    }

    private fun writeYaml(dir: Path, filename: String, content: String): Path {
        val file = dir.resolve(filename)
        Files.writeString(file, content)
        return file
    }
}
