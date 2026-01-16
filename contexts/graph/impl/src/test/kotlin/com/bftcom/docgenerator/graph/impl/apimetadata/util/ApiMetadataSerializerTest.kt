package com.bftcom.docgenerator.graph.impl.apimetadata.util

import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiMetadataSerializerTest {
    @Test
    fun `serialize returns null for null metadata`() {
        val result = ApiMetadataSerializer.serialize(null)

        assertThat(result).isNull()
    }

    @Test
    fun `serialize HttpEndpoint with all fields`() {
        val metadata = ApiMetadata.HttpEndpoint(
            method = "GET",
            path = "/api/users",
            basePath = "/api/v1",
            consumes = listOf("application/json"),
            produces = listOf("application/json"),
            headers = mapOf("Authorization" to "Bearer token"),
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("HttpEndpoint")
        assertThat(result["method"]).isEqualTo("GET")
        assertThat(result["path"]).isEqualTo("/api/users")
        assertThat(result["basePath"]).isEqualTo("/api/v1")
        assertThat(result["consumes"]).isEqualTo(listOf("application/json"))
        assertThat(result["produces"]).isEqualTo(listOf("application/json"))
        assertThat(result["headers"]).isEqualTo(mapOf("Authorization" to "Bearer token"))
    }

    @Test
    fun `serialize HttpEndpoint filters empty strings`() {
        val metadata = ApiMetadata.HttpEndpoint(
            method = "GET",
            path = "/api/users",
            basePath = "",
            consumes = emptyList(),
            produces = emptyList(),
            headers = emptyMap(),
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("HttpEndpoint")
        assertThat(result["method"]).isEqualTo("GET")
        assertThat(result["path"]).isEqualTo("/api/users")
        assertThat(result).doesNotContainKeys("basePath", "consumes", "produces", "headers")
    }

    @Test
    fun `serialize HttpEndpoint with null optional fields`() {
        val metadata = ApiMetadata.HttpEndpoint(
            method = "POST",
            path = "/api/users",
            basePath = null,
            consumes = null,
            produces = null,
            headers = null,
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("HttpEndpoint")
        assertThat(result["method"]).isEqualTo("POST")
        assertThat(result["path"]).isEqualTo("/api/users")
        assertThat(result).doesNotContainKeys("basePath", "consumes", "produces", "headers")
    }

    @Test
    fun `serialize GraphQLEndpoint with all fields`() {
        val metadata = ApiMetadata.GraphQLEndpoint(
            query = "getUser",
            mutation = "updateUser",
            subscription = "userUpdates",
            schema = "/graphql/schema",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("GraphQLEndpoint")
        assertThat(result["query"]).isEqualTo("getUser")
        assertThat(result["mutation"]).isEqualTo("updateUser")
        assertThat(result["subscription"]).isEqualTo("userUpdates")
        assertThat(result["schema"]).isEqualTo("/graphql/schema")
    }

    @Test
    fun `serialize GraphQLEndpoint filters empty strings`() {
        val metadata = ApiMetadata.GraphQLEndpoint(
            query = "",
            mutation = "",
            subscription = "",
            schema = "",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        // GraphQLEndpoint фильтрует только строки, но @type остается
        assertThat(result).isNotNull
        assertThat(result!!.keys).containsOnly("@type")
    }

    @Test
    fun `serialize GraphQLEndpoint with null fields`() {
        val metadata = ApiMetadata.GraphQLEndpoint(
            query = null,
            mutation = null,
            subscription = null,
            schema = null,
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        // GraphQLEndpoint фильтрует только строки, но @type остается
        assertThat(result).isNotNull
        assertThat(result!!.keys).containsOnly("@type")
    }

    @Test
    fun `serialize GraphQLEndpoint with partial fields`() {
        val metadata = ApiMetadata.GraphQLEndpoint(
            query = "getUser",
            mutation = null,
            subscription = null,
            schema = null,
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("GraphQLEndpoint")
        assertThat(result["query"]).isEqualTo("getUser")
        assertThat(result).doesNotContainKeys("mutation", "subscription", "schema")
    }

    @Test
    fun `serialize GrpcEndpoint with all fields`() {
        val metadata = ApiMetadata.GrpcEndpoint(
            service = "UserService",
            method = "getUser",
            packageName = "com.example.grpc",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("GrpcEndpoint")
        assertThat(result["service"]).isEqualTo("UserService")
        assertThat(result["method"]).isEqualTo("getUser")
        assertThat(result["packageName"]).isEqualTo("com.example.grpc")
    }

    @Test
    fun `serialize GrpcEndpoint filters empty packageName`() {
        val metadata = ApiMetadata.GrpcEndpoint(
            service = "UserService",
            method = "getUser",
            packageName = "",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("GrpcEndpoint")
        assertThat(result["service"]).isEqualTo("UserService")
        assertThat(result["method"]).isEqualTo("getUser")
        assertThat(result).doesNotContainKey("packageName")
    }

    @Test
    fun `serialize GrpcEndpoint with null packageName`() {
        val metadata = ApiMetadata.GrpcEndpoint(
            service = "UserService",
            method = "getUser",
            packageName = null,
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("GrpcEndpoint")
        assertThat(result["service"]).isEqualTo("UserService")
        assertThat(result["method"]).isEqualTo("getUser")
        assertThat(result).doesNotContainKey("packageName")
    }

    @Test
    fun `serialize MessageBrokerEndpoint with Kafka`() {
        val metadata = ApiMetadata.MessageBrokerEndpoint(
            broker = ApiMetadata.BrokerType.KAFKA,
            topic = "user-events",
            queue = null,
            consumerGroup = "my-group",
            exchange = null,
            routingKey = null,
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("MessageBrokerEndpoint")
        assertThat(result["broker"]).isEqualTo("KAFKA")
        assertThat(result["topic"]).isEqualTo("user-events")
        assertThat(result["consumerGroup"]).isEqualTo("my-group")
        assertThat(result).doesNotContainKeys("queue", "exchange", "routingKey")
    }

    @Test
    fun `serialize MessageBrokerEndpoint with RabbitMQ`() {
        val metadata = ApiMetadata.MessageBrokerEndpoint(
            broker = ApiMetadata.BrokerType.RABBITMQ,
            topic = null,
            queue = "user-queue",
            consumerGroup = null,
            exchange = "my-exchange",
            routingKey = "user.*",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("MessageBrokerEndpoint")
        assertThat(result["broker"]).isEqualTo("RABBITMQ")
        assertThat(result["queue"]).isEqualTo("user-queue")
        assertThat(result["exchange"]).isEqualTo("my-exchange")
        assertThat(result["routingKey"]).isEqualTo("user.*")
        assertThat(result).doesNotContainKeys("topic", "consumerGroup")
    }

    @Test
    fun `serialize MessageBrokerEndpoint filters empty strings`() {
        val metadata = ApiMetadata.MessageBrokerEndpoint(
            broker = ApiMetadata.BrokerType.NATS,
            topic = "",
            queue = "",
            consumerGroup = "",
            exchange = "",
            routingKey = "",
        )

        val result = ApiMetadataSerializer.serialize(metadata)

        assertThat(result).isNotNull
        assertThat(result!!["@type"]).isEqualTo("MessageBrokerEndpoint")
        assertThat(result["broker"]).isEqualTo("NATS")
        assertThat(result).doesNotContainKeys("topic", "queue", "consumerGroup", "exchange", "routingKey")
    }

    @Test
    fun `serialize MessageBrokerEndpoint with all broker types`() {
        val brokerTypes = listOf(
            ApiMetadata.BrokerType.KAFKA,
            ApiMetadata.BrokerType.RABBITMQ,
            ApiMetadata.BrokerType.NATS,
            ApiMetadata.BrokerType.SQS,
        )

        brokerTypes.forEach { brokerType ->
            val metadata = ApiMetadata.MessageBrokerEndpoint(
                broker = brokerType,
                topic = "test-topic",
            )

            val result = ApiMetadataSerializer.serialize(metadata)

            assertThat(result).isNotNull
            assertThat(result!!["broker"]).isEqualTo(brokerType.name)
        }
    }
}
