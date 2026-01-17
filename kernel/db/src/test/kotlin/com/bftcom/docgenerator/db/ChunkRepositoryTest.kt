package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

class ChunkRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var chunkRepository: ChunkRepository

    @Autowired
    private lateinit var nodeRepository: NodeRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    private lateinit var application: Application
    private lateinit var node1: Node
    private lateinit var node2: Node

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        application = Application(
            key = "test-app-${System.currentTimeMillis()}",
            name = "Test Application",
        )
        application = applicationRepository.save(application)

        node1 = Node(
            application = application,
            fqn = "com.example.Class1",
            name = "Class1",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node1 = nodeRepository.save(node1)

        node2 = Node(
            application = application,
            fqn = "com.example.Class2",
            name = "Class2",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        node2 = nodeRepository.save(node2)
    }

    @Test
    fun `findTopByNodeIdOrderByCreatedAtDesc - возвращает последний chunk для node`() {
        // Given
        val chunk1 = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content 1",
            createdAt = OffsetDateTime.now().minusHours(1),
        )
        val chunk2 = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "tech",
            langDetected = "ru",
            content = "Content 2",
            createdAt = OffsetDateTime.now(),
        )
        chunkRepository.save(chunk1)
        chunkRepository.save(chunk2)

        // When
        val found = chunkRepository.findTopByNodeIdOrderByCreatedAtDesc(node1.id!!)

        // Then
        assertThat(found).isNotNull
        assertThat(found!!.content).isEqualTo("Content 2")
        assertThat(found.kind).isEqualTo("tech")
    }

    @Test
    fun `findByNodeId - возвращает все chunks для node`() {
        // Given
        val chunk1 = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content 1",
        )
        val chunk2 = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "tech",
            langDetected = "ru",
            content = "Content 2",
        )
        chunkRepository.save(chunk1)
        chunkRepository.save(chunk2)

        // When
        val chunks = chunkRepository.findByNodeId(node1.id!!)

        // Then
        assertThat(chunks).hasSize(2)
        assertThat(chunks.map { it.kind }).containsExactlyInAnyOrder("public", "tech")
    }

    @Test
    fun `lockNextBatchForPostprocess - возвращает chunks без content_hash или token_count`() {
        // Given: chunk без content_hash
        val chunk1 = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content 1",
            contentHash = null,
            tokenCount = null,
        )
        chunkRepository.save(chunk1)

        // chunk с content_hash и token_count
        val chunk2 = Chunk(
            application = application,
            node = node2,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content 2",
            contentHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
            tokenCount = 100,
        )
        chunkRepository.save(chunk2)
        chunkRepository.flush()

        // When
        val chunks = chunkRepository.lockNextBatchForPostprocess(limit = 10, withEmb = false)

        // Then
        assertThat(chunks).isNotEmpty
        assertThat(chunks.all { it.contentHash == null || it.tokenCount == null }).isTrue
    }

    @Test
    fun `updateMeta - обновляет метаданные chunk`() {
        // Given
        val chunk = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content",
        )
        val saved = chunkRepository.save(chunk)
        chunkRepository.flush()

        // When
        val updated = chunkRepository.updateMeta(
            id = saved.id!!,
            contentHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
            tokenCount = 150,
            embedModel = "model-1",
            embedTs = OffsetDateTime.now(),
        )

        // Then
        assertThat(updated).isEqualTo(1)

        val found = chunkRepository.findById(saved.id!!).orElse(null)
        assertThat(found).isNotNull
        assertThat(found!!.contentHash).isEqualTo("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
        assertThat(found.tokenCount).isEqualTo(150)
        assertThat(found.embedModel).isEqualTo("model-1")
    }

    @Test
    fun `upsertDocChunk - создаёт новый chunk`() {
        // Given
        val metadataJson = objectMapper.writeValueAsString(
            mapOf("node_id" to node1.id, "locale" to "ru")
        )

        // When
        val result = chunkRepository.upsertDocChunk(
            applicationId = application.id!!,
            nodeId = node1.id!!,
            locale = "ru",
            kind = "public",
            content = "New content",
            metadataJson = metadataJson,
        )

        // Then
        assertThat(result).isEqualTo(1)

        val chunks = chunkRepository.findByNodeId(node1.id!!)
        assertThat(chunks).isNotEmpty
        val chunk = chunks.find { it.kind == "public" && it.langDetected == "ru" }
        assertThat(chunk).isNotNull
        assertThat(chunk!!.content).isEqualTo("New content")
    }

    @Test
    fun `upsertDocChunk - обновляет существующий chunk при конфликте`() {
        // Given: создаём chunk
        val chunk = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Old content",
        )
        chunkRepository.save(chunk)
        chunkRepository.flush()

        val metadataJson = objectMapper.writeValueAsString(
            mapOf("node_id" to node1.id, "locale" to "ru")
        )

        // When: обновляем с теми же параметрами
        val result = chunkRepository.upsertDocChunk(
            applicationId = application.id!!,
            nodeId = node1.id!!,
            locale = "ru",
            kind = "public",
            content = "New content",
            metadataJson = metadataJson,
        )

        // Then
        assertThat(result).isEqualTo(1)

        val chunks = chunkRepository.findByNodeId(node1.id!!)
        val updated = chunks.find { it.kind == "public" && it.langDetected == "ru" }
        assertThat(updated).isNotNull
        assertThat(updated!!.content).isEqualTo("New content")
    }

    @Test
    fun `clearAllPostprocessData - очищает все поля постпроцесса`() {
        // Given
        val chunk = Chunk(
            application = application,
            node = node1,
            source = "doc",
            kind = "public",
            langDetected = "ru",
            content = "Content",
            contentHash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
            tokenCount = 100,
            embedModel = "model-1",
            embedTs = OffsetDateTime.now(),
        )
        val saved = chunkRepository.save(chunk)
        chunkRepository.flush() // Сбрасываем в БД

        // When
        val result = chunkRepository.clearAllPostprocessData()

        // !!! ВОТ ТУТ РЕШЕНИЕ !!!
        entityManager.clear()
        // Очищаем кэш, чтобы следующий findById реально пошел в базу через SELECT

        // Then
        assertThat(result).isGreaterThanOrEqualTo(1)

        val found = chunkRepository.findById(saved.id!!).orElse(null)
        assertThat(found).isNotNull
        assertThat(found!!.contentHash).isNull()
        assertThat(found.tokenCount).isNull()
        assertThat(found.embedModel).isNull()
        assertThat(found.embedTs).isNull()
    }
}