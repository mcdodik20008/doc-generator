package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageBrokerExtractorTest {
    private val extractor = MessageBrokerExtractor()

    @Test
    fun `id returns message-broker`() {
        assertThat(extractor.id()).isEqualTo("message-broker")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @Test
    fun `extractFunctionMetadata returns Kafka endpoint for KafkaListener annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.kafka.annotation.KafkaListener(topics = ["user-events"])""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.broker).isEqualTo(ApiMetadata.BrokerType.KAFKA)
        assertThat(endpoint.topic).isEqualTo("user-events")
    }

    @Test
    fun `extractFunctionMetadata extracts groupId from KafkaListener`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.kafka.annotation.KafkaListener(topics = ["events"], groupId = "my-group")""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.consumerGroup).isEqualTo("my-group")
    }

    @Test
    fun `extractFunctionMetadata returns RabbitMQ endpoint for RabbitListener annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.amqp.rabbit.annotation.RabbitListener(queues = ["user-queue"])""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.broker).isEqualTo(ApiMetadata.BrokerType.RABBITMQ)
        assertThat(endpoint.queue).isEqualTo("user-queue")
    }

    @Test
    fun `extractFunctionMetadata extracts exchange and routingKey from RabbitListener`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.amqp.rabbit.annotation.RabbitListener(exchange = "my-exchange", routingKey = "user.*")""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.exchange).isEqualTo("my-exchange")
        assertThat(endpoint.routingKey).isEqualTo("user.*")
    }

    @Test
    fun `extractFunctionMetadata returns NATS endpoint for NatsListener annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""io.nats.client.NatsListener(subject = "user.events")""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.broker).isEqualTo(ApiMetadata.BrokerType.NATS)
        assertThat(endpoint.topic).isEqualTo("user.events")
    }

    @Test
    fun `extractFunctionMetadata returns NATS endpoint when imports contain io nats`() {
        val function = createRawFunction()
        val ctx = createContext(imports = listOf("io.nats.client.Connection"))

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.broker).isEqualTo(ApiMetadata.BrokerType.NATS)
    }

    @Test
    fun `extractFunctionMetadata returns null for non-broker annotations`() {
        val function = createRawFunction(
            annotationsRepr = setOf("org.springframework.stereotype.Service")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `extractFunctionMetadata handles single quotes in annotation parameters`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.kafka.annotation.KafkaListener(topics = ['user-events'])""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.MessageBrokerEndpoint::class.java)
        val endpoint = result as ApiMetadata.MessageBrokerEndpoint
        assertThat(endpoint.topic).isEqualTo("user-events")
    }

    @Test
    fun `extractTypeMetadata returns null`() {
        val type = createRawType()
        val ctx = createContext()

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isNull()
    }

    private fun createRawFunction(
        annotationsRepr: Set<String> = emptySet(),
    ): RawFunction {
        return RawFunction(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            ownerFqn = null,
            name = "testMethod",
            signatureRepr = "fun testMethod()",
            paramNames = emptyList(),
            annotationsRepr = annotationsRepr,
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = null,
            span = null,
            text = null,
        )
    }

    private fun createRawType(): com.bftcom.docgenerator.graph.api.model.rawdecl.RawType {
        return com.bftcom.docgenerator.graph.api.model.rawdecl.RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            simpleName = "TestClass",
            kindRepr = "class",
            supertypesRepr = emptyList(),
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
