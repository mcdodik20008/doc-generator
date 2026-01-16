package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.NodeDocRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class ChunkFromNodeDocSchedulerTest {
    private lateinit var txManager: PlatformTransactionManager
    private lateinit var nodeDocRepo: NodeDocRepository
    private lateinit var chunkRepo: ChunkRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var scheduler: ChunkFromNodeDocScheduler

    @BeforeEach
    fun setUp() {
        txManager = mockk {
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }
        nodeDocRepo = mockk(relaxed = true)
        chunkRepo = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerKotlinModule()

        scheduler =
            ChunkFromNodeDocScheduler(
                txManager = txManager,
                nodeDocRepo = nodeDocRepo,
                chunkRepo = chunkRepo,
                objectMapper = objectMapper,
            )
    }

    @Test
    fun `poll - обрабатывает пустой батч`() {
        every { nodeDocRepo.lockNextBatchForChunkSync(50) } returns emptyList()

        scheduler.poll()

        verify(exactly = 0) { chunkRepo.upsertDocChunk(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `poll - обрабатывает батч с docPublic и docTech`() {
        val row = mockk<NodeDocRepository.DocChunkSyncRow> {
            every { getNodeId() } returns 100L
            every { getApplicationId() } returns 1L
            every { getLocale() } returns "ru"
            every { getDocPublic() } returns "public doc content"
            every { getDocTech() } returns "tech doc content"
        }

        every { nodeDocRepo.lockNextBatchForChunkSync(50) } returns listOf(row)
        every {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "public",
                content = "public doc content",
                metadataJson = any(),
            )
        } returns 1
        every {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "tech",
                content = "tech doc content",
                metadataJson = any(),
            )
        } returns 1

        scheduler.poll()

        verify(exactly = 1) {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "public",
                content = "public doc content",
                metadataJson = any(),
            )
        }
        verify(exactly = 1) {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "tech",
                content = "tech doc content",
                metadataJson = any(),
            )
        }
    }

    @Test
    fun `poll - пропускает пустые docPublic и docTech`() {
        val row = mockk<NodeDocRepository.DocChunkSyncRow> {
            every { getNodeId() } returns 100L
            every { getApplicationId() } returns 1L
            every { getLocale() } returns "ru"
            every { getDocPublic() } returns "  " // Пустая строка
            every { getDocTech() } returns "" // Пустая строка
        }

        every { nodeDocRepo.lockNextBatchForChunkSync(50) } returns listOf(row)

        scheduler.poll()

        verify(exactly = 0) { chunkRepo.upsertDocChunk(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `poll - обрабатывает только docPublic`() {
        val row = mockk<NodeDocRepository.DocChunkSyncRow> {
            every { getNodeId() } returns 100L
            every { getApplicationId() } returns 1L
            every { getLocale() } returns "ru"
            every { getDocPublic() } returns "public doc"
            every { getDocTech() } returns null
        }

        every { nodeDocRepo.lockNextBatchForChunkSync(50) } returns listOf(row)
        every {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "public",
                content = "public doc",
                metadataJson = any(),
            )
        } returns 1

        scheduler.poll()

        verify(exactly = 1) {
            chunkRepo.upsertDocChunk(
                applicationId = 1L,
                nodeId = 100L,
                locale = "ru",
                kind = "public",
                content = "public doc",
                metadataJson = any(),
            )
        }
        verify(exactly = 0) {
            chunkRepo.upsertDocChunk(
                applicationId = any(),
                nodeId = any(),
                locale = any(),
                kind = "tech",
                content = any(),
                metadataJson = any(),
            )
        }
    }
}
