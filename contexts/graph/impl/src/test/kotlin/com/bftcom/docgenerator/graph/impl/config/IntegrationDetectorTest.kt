package com.bftcom.docgenerator.graph.impl.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IntegrationDetectorTest {
    @Test
    fun `detects HTTP integration from url property with URL value`() {
        val props =
            mapOf(
                "rr.ups-client.api-url" to "\${UPS_SERVICE_API_URL:https://k8s.supercode.ru:3300/bpm/user-profile-service}",
                "rr.ups-client.timeout" to "5000",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        val integration = result[0]
        assertThat(integration.type).isEqualTo(InfraIntegrationType.HTTP)
        assertThat(integration.groupKey).isEqualTo("rr.ups-client")
        assertThat(integration.defaultUrl).isEqualTo("https://k8s.supercode.ru:3300/bpm/user-profile-service")
        assertThat(integration.envVar).isEqualTo("UPS_SERVICE_API_URL")
        assertThat(integration.properties).containsKey("rr.ups-client.api-url")
        assertThat(integration.properties).containsKey("rr.ups-client.timeout")
    }

    @Test
    fun `detects database integration from spring datasource`() {
        val props =
            mapOf(
                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/mydb",
                "spring.datasource.username" to "user",
                "spring.datasource.password" to "pass",
                "spring.datasource.driver-class-name" to "org.postgresql.Driver",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        val integration = result[0]
        assertThat(integration.type).isEqualTo(InfraIntegrationType.DATABASE)
        assertThat(integration.groupKey).isEqualTo("spring.datasource")
        assertThat(integration.defaultUrl).isEqualTo("jdbc:postgresql://localhost:5432/mydb")
    }

    @Test
    fun `detects Kafka integration from bootstrap-servers`() {
        val props =
            mapOf(
                "spring.kafka.bootstrap-servers" to "\${KAFKA_SERVERS:localhost:9092}",
                "spring.kafka.consumer.group-id" to "my-group",
                "spring.kafka.consumer.auto-offset-reset" to "earliest",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        val integration = result[0]
        assertThat(integration.type).isEqualTo(InfraIntegrationType.KAFKA)
        assertThat(integration.groupKey).isEqualTo("spring.kafka")
        assertThat(integration.envVar).isEqualTo("KAFKA_SERVERS")
        assertThat(integration.defaultUrl).isEqualTo("localhost:9092")
    }

    @Test
    fun `detects RabbitMQ integration from host+port+exchange`() {
        val props =
            mapOf(
                "rr.users.camel-rabbit-consumer-settings.host" to "\${RABBIT_HOST:localhost}",
                "rr.users.camel-rabbit-consumer-settings.port" to "5672",
                "rr.users.camel-rabbit-consumer-settings.exchange" to "users-exchange",
                "rr.users.camel-rabbit-consumer-settings.queue" to "users-queue",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        val integration = result[0]
        assertThat(integration.type).isEqualTo(InfraIntegrationType.RABBIT)
        assertThat(integration.groupKey).isEqualTo("rr.users.camel-rabbit-consumer-settings")
    }

    @Test
    fun `extracts default value from placeholder`() {
        val (envVar, defaultUrl) =
            IntegrationDetector.extractEnvAndDefault(
                "\${MY_URL:https://example.com/api}",
            )

        assertThat(envVar).isEqualTo("MY_URL")
        assertThat(defaultUrl).isEqualTo("https://example.com/api")
    }

    @Test
    fun `extracts env var without default`() {
        val (envVar, defaultUrl) = IntegrationDetector.extractEnvAndDefault("\${MY_URL}")

        assertThat(envVar).isEqualTo("MY_URL")
        assertThat(defaultUrl).isNull()
    }

    @Test
    fun `groups related properties under common prefix`() {
        val props =
            mapOf(
                "rr.unsi.client.rest-uri" to "\${UNSI_URL:https://unsi.example.com}",
                "rr.unsi.client.timeout" to "3000",
                "rr.unsi.client.retry-count" to "3",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        assertThat(result[0].groupKey).isEqualTo("rr.unsi.client")
        assertThat(result[0].properties).hasSize(3)
    }

    @Test
    fun `skips non-integration properties`() {
        val props =
            mapOf(
                "spring.jmx.enabled" to "false",
                "debug" to "true",
                "server.port" to "8080",
                "logging.level.root" to "INFO",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).isEmpty()
    }

    @Test
    fun `detects Camunda integration`() {
        val props =
            mapOf(
                "camunda.bpm.client.base-url" to "\${CAMUNDA_URL:http://localhost:8080/engine-rest}",
                "camunda.bpm.client.worker-id" to "worker-1",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(InfraIntegrationType.CAMUNDA)
        assertThat(result[0].defaultUrl).isEqualTo("http://localhost:8080/engine-rest")
    }

    @Test
    fun `detects OAuth2 auth integration`() {
        val props =
            mapOf(
                "spring.security.oauth2.client.provider.avanpost.issuer-uri" to "https://auth.example.com",
                "spring.security.oauth2.client.provider.avanpost.token-uri" to "https://auth.example.com/token",
                "spring.security.oauth2.client.registration.avanpost.client-id" to "my-app",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).isNotEmpty
        assertThat(result.any { it.type == InfraIntegrationType.AUTH }).isTrue()
    }

    @Test
    fun `detects Config Server integration`() {
        val props =
            mapOf(
                "spring.cloud.config.uri" to "\${CONFIG_SERVER_URI:http://localhost:8888}",
                "spring.cloud.config.fail-fast" to "true",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(1)
        assertThat(result[0].type).isEqualTo(InfraIntegrationType.CONFIG_SERVER)
        assertThat(result[0].defaultUrl).isEqualTo("http://localhost:8888")
    }

    @Test
    fun `detects multiple integrations from mixed config`() {
        val props =
            mapOf(
                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/db",
                "spring.datasource.username" to "user",
                "spring.kafka.bootstrap-servers" to "localhost:9092",
                "rr.ups-client.api-url" to "https://ups.example.com",
                "rr.ups-client.timeout" to "5000",
            )

        val result = IntegrationDetector.detect(props)

        assertThat(result).hasSize(3)
        val types = result.map { it.type }.toSet()
        assertThat(types).containsExactlyInAnyOrder(
            InfraIntegrationType.DATABASE,
            InfraIntegrationType.KAFKA,
            InfraIntegrationType.HTTP,
        )
    }

    @Test
    fun `readable name strips common prefixes`() {
        assertThat(IntegrationDetector.readableName("rr.ups-client", InfraIntegrationType.HTTP))
            .isEqualTo("ups-client (HTTP)")
        assertThat(IntegrationDetector.readableName("spring.datasource", InfraIntegrationType.DATABASE))
            .isEqualTo("datasource (DATABASE)")
        assertThat(IntegrationDetector.readableName("camunda", InfraIntegrationType.CAMUNDA))
            .isEqualTo("camunda (CAMUNDA)")
    }

    @Test
    fun `resolveDefault extracts default from placeholder`() {
        val result = IntegrationDetector.resolveDefault("\${MY_URL:https://example.com}")
        assertThat(result).isEqualTo("https://example.com")
    }

    @Test
    fun `resolveDefault keeps placeholder without default`() {
        val result = IntegrationDetector.resolveDefault("\${MY_URL}")
        assertThat(result).isEqualTo("\${MY_URL}")
    }
}
