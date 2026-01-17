package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.nodedoc.NodeDoc
import com.bftcom.docgenerator.domain.nodedoc.NodeDocId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

class NodeDocRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var nodeDocRepository: NodeDocRepository

    @Autowired
    private lateinit var nodeRepository: NodeRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Autowired
    private lateinit var chunkRepository: ChunkRepository

    private lateinit var application: Application
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        // Given: Создаём Application
        application = Application(
            key = "test-app-${System.currentTimeMillis()}",
            name = "Test Application",
        )
        application = applicationRepository.save(application)

        // Given: Создаём несколько Node
        node1 = Node(
            application = application,
            fqn = "com.example.TestClass1",
            name = "TestClass1",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node1 = nodeRepository.save(node1)

        node2 = Node(
            application = application,
            fqn = "com.example.TestClass2",
            name = "TestClass2",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node2 = nodeRepository.save(node2)

        node3 = Node(
            application = application,
            fqn = "com.example.TestMethod",
            name = "testMethod",
            packageName = "com.example",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = node1,
        )
        node3 = nodeRepository.save(node3)
    }

    @Test
    fun `findDigest - возвращает digest для существующей записи`() {
        // Given: создаём NodeDoc
        val nodeDoc = NodeDoc(
            node = node1,
            locale = "ru",
            docPublic = "Public documentation",
            docTech = "Technical documentation",
            docDigest = "digest-123",
            modelMeta = mapOf("model" to "gpt-4", "temperature" to 0.7),
        )
        nodeDocRepository.save(nodeDoc)

        // When
        val digest = nodeDocRepository.findDigest(node1.id!!, "ru")

        // Then
        assertThat(digest).isEqualTo("digest-123")
    }

    @Test
    fun `findDigest - возвращает null для несуществующей записи`() {
        // When
        val digest = nodeDocRepository.findDigest(node1.id!!, "en")

        // Then
        assertThat(digest).isNull()
    }

    @Test
    fun `upsert - создаёт новую запись при первом вызове`() {
        // Given
        val modelMetaJson = objectMapper.writeValueAsString(
            mapOf("model" to "gpt-4", "temperature" to 0.7)
        )

        // When
        val result = nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Public doc",
            docTech = "Tech doc",
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )

        // Then
        assertThat(result).isEqualTo(1)

        val saved = nodeDocRepository.findById(NodeDocId(node = node1.id, locale = "ru")).orElse(null)
        assertThat(saved).isNotNull
        assertThat(saved!!.docPublic).isEqualTo("Public doc")
        assertThat(saved.docTech).isEqualTo("Tech doc")
        assertThat(saved.docDigest).isEqualTo("digest-1")
        assertThat(saved.modelMeta).containsEntry("model", "gpt-4")
        assertThat(saved.modelMeta).containsEntry("temperature", 0.7)
    }

    @Test
    fun `upsert - обновляет существующую запись при конфликте`() {
        // Given: создаём первую запись
        val modelMetaJson1 = objectMapper.writeValueAsString(
            mapOf("model" to "gpt-3.5", "temperature" to 0.5)
        )
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Old public doc",
            docTech = "Old tech doc",
            docDigest = "old-digest",
            modelMetaJson = modelMetaJson1,
        )

        // When: обновляем с теми же nodeId и locale
        val modelMetaJson2 = objectMapper.writeValueAsString(
            mapOf("model" to "gpt-4", "temperature" to 0.8)
        )
        val result = nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "New public doc",
            docTech = "New tech doc",
            docDigest = "new-digest",
            modelMetaJson = modelMetaJson2,
        )

        // Then
        assertThat(result).isEqualTo(1)

        val updated = nodeDocRepository.findById(NodeDocId(node = node1.id, locale = "ru")).orElse(null)
        assertThat(updated).isNotNull
        assertThat(updated!!.docPublic).isEqualTo("New public doc")
        assertThat(updated.docTech).isEqualTo("New tech doc")
        assertThat(updated.docDigest).isEqualTo("new-digest")
        assertThat(updated.modelMeta).containsEntry("model", "gpt-4")
        assertThat(updated.modelMeta).containsEntry("temperature", 0.8)
    }

    @Test
    fun `upsert - корректно обрабатывает jsonb поля`() {
        // Given: сложная структура в modelMeta
        val complexMeta = mapOf(
            "model" to "gpt-4",
            "temperature" to 0.7,
            "maxTokens" to 2000,
            "params" to mapOf(
                "topP" to 0.9,
                "frequencyPenalty" to 0.5,
            ),
            "metadata" to listOf("tag1", "tag2"),
        )
        val modelMetaJson = objectMapper.writeValueAsString(complexMeta)

        // When
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Test",
            docTech = null,
            docDigest = "digest",
            modelMetaJson = modelMetaJson,
        )

        // Then
        val saved = nodeDocRepository.findById(NodeDocId(node = node1.id, locale = "ru")).orElse(null)
        assertThat(saved).isNotNull
        assertThat(saved!!.modelMeta).isEqualTo(complexMeta)
    }

    @Test
    fun `lockNextBatchForChunkSync - возвращает записи без соответствующих chunk`() {
        // Given: создаём NodeDoc с docPublic, но без соответствующего Chunk
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Public documentation",
            docTech = null,
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )

        // Зафиксируем транзакцию, чтобы данные были видны
        nodeDocRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 10)

        // Then
        assertThat(rows).hasSize(1)
        assertThat(rows[0].getNodeId()).isEqualTo(node1.id)
        assertThat(rows[0].getApplicationId()).isEqualTo(application.id)
        assertThat(rows[0].getLocale()).isEqualTo("ru")
        assertThat(rows[0].getDocPublic()).isEqualTo("Public documentation")
        assertThat(rows[0].getDocTech()).isNull()
    }

    @Test
    fun `lockNextBatchForChunkSync - возвращает записи с устаревшими chunk`() {
        // Given: создаём NodeDoc
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Initial public doc",
            docTech = null,
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )
        nodeDocRepository.flush()

        // Создаём Chunk с более старым updated_at
        val oldChunk = com.bftcom.docgenerator.domain.chunk.Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Old content",
            updatedAt = OffsetDateTime.now().minusDays(1),
        )
        chunkRepository.save(oldChunk)
        chunkRepository.flush()

        // Обновляем NodeDoc (чтобы updated_at был новее)
        Thread.sleep(10) // Небольшая задержка для гарантии разницы во времени
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Updated public doc",
            docTech = null,
            docDigest = "digest-2",
            modelMetaJson = modelMetaJson,
        )
        nodeDocRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 10)

        // Then: должен вернуться, так как chunk устарел
        assertThat(rows).hasSize(1)
        assertThat(rows[0].getNodeId()).isEqualTo(node1.id)
        assertThat(rows[0].getDocPublic()).isEqualTo("Updated public doc")
    }

    @Test
    fun `lockNextBatchForChunkSync - не возвращает записи с актуальными chunk`() {
        // Given: создаём NodeDoc и соответствующий Chunk с актуальным updated_at
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Public doc",
            docTech = null,
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )
        nodeDocRepository.flush()

        val chunk = com.bftcom.docgenerator.domain.chunk.Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content",
            updatedAt = OffsetDateTime.now(),
        )
        chunkRepository.save(chunk)
        chunkRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 10)

        // Then: не должен вернуться, так как chunk актуален
        assertThat(rows).isEmpty()
    }

    @Test
    fun `lockNextBatchForChunkSync - возвращает записи с docTech без chunk`() {
        // Given: создаём NodeDoc только с docTech
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = null,
            docTech = "Tech documentation",
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )
        nodeDocRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 10)

        // Then
        assertThat(rows).hasSize(1)
        assertThat(rows[0].getDocTech()).isEqualTo("Tech documentation")
        assertThat(rows[0].getDocPublic()).isNull()
    }

    @Test
    fun `lockNextBatchForChunkSync - возвращает записи с обоими типами документации`() {
        // Given: создаём NodeDoc с docPublic и docTech
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        nodeDocRepository.upsert(
            nodeId = node1.id!!,
            locale = "ru",
            docPublic = "Public doc",
            docTech = "Tech doc",
            docDigest = "digest-1",
            modelMetaJson = modelMetaJson,
        )
        nodeDocRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 10)

        // Then
        assertThat(rows).hasSize(1)
        assertThat(rows[0].getDocPublic()).isEqualTo("Public doc")
        assertThat(rows[0].getDocTech()).isEqualTo("Tech doc")
    }

    @Test
    fun `lockNextBatchForChunkSync - уважает limit`() {
        // Given: создаём несколько NodeDoc
        val modelMetaJson = objectMapper.writeValueAsString(mapOf("model" to "gpt-4"))
        for (i in 1..5) {
            val node = Node(
                application = application,
                fqn = "com.example.Class$i",
                name = "Class$i",
                packageName = "com.example",
                kind = NodeKind.CLASS,
                lang = Lang.kotlin,
            )
            val savedNode = nodeRepository.save(node)

            nodeDocRepository.upsert(
                nodeId = savedNode.id!!,
                locale = "ru",
                docPublic = "Doc $i",
                docTech = null,
                docDigest = "digest-$i",
                modelMetaJson = modelMetaJson,
            )
        }
        nodeDocRepository.flush()

        // When
        val rows = nodeDocRepository.lockNextBatchForChunkSync(limit = 3)

        // Then
        assertThat(rows).hasSizeLessThanOrEqualTo(3)
    }
}