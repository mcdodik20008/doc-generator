package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessagingTopicExtractorTest {
    private val extractor = MessagingTopicExtractor()

    @Test
    fun `id returns messaging-topic`() {
        assertThat(extractor.id()).isEqualTo("messaging-topic")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns TOPIC for Spring Kafka Consumer`() {
        val raw = createRawType(
            simpleName = "MyConsumer",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for Apache Kafka Producer`() {
        val raw = createRawType(
            simpleName = "MyProducer",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.apache.kafka.clients.producer.Producer")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for Spring RabbitMQ Listener`() {
        val raw = createRawType(
            simpleName = "MyListener",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.rabbitmq.annotation.RabbitListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for NATS`() {
        val raw = createRawType(
            simpleName = "MySubscriber",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("io.nats.client.Connection")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for class in kafka package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.kafka"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for class in messaging package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.messaging"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for class in mq package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.mq"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns null when no messaging imports`() {
        val raw = createRawType(
            simpleName = "MyConsumer",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.stereotype.Service")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns null when messaging imports but wrong naming`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns TOPIC for name ending with Consumer`() {
        val raw = createRawType(
            simpleName = "MyConsumer",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for name ending with Producer`() {
        val raw = createRawType(
            simpleName = "MyProducer",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType returns TOPIC for name ending with Listener`() {
        val raw = createRawType(
            simpleName = "MyListener",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    @Test
    fun `refineType handles case-insensitive name matching`() {
        val raw = createRawType(
            simpleName = "MYCONSUMER",
            pkgFqn = "com.example"
        )
        val ctx = createContext(
            imports = listOf("org.springframework.kafka.annotation.KafkaListener")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TOPIC)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = annotationsRepr,
            span = null,
            text = null,
        )
    }

    private fun createContext(
        imports: List<String>? = null,
    ): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = imports,
        )
    }
}
