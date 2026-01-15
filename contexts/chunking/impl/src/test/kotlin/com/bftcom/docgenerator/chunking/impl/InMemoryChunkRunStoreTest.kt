package com.bftcom.docgenerator.chunking.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryChunkRunStoreTest {
    @Test
    fun `status - lifecycle created running completed`() {
        val store = InMemoryChunkRunStore()

        val run = store.create(applicationId = 1L, strategy = "per-node")
        assertTrue(run.runId.isNotBlank())
        assertNotNull(run.startedAt)

        val created = store.status(run.runId)
        assertEquals("created", created.state)
        assertEquals(0, created.processedNodes)
        assertEquals(0, created.writtenChunks)
        assertEquals(0, created.skippedChunks)
        assertTrue(created.errors.isEmpty())
        assertNotNull(created.startedAt)
        assertEquals(null, created.finishedAt)

        store.markRunning(run.runId)
        val running = store.status(run.runId)
        assertEquals("running", running.state)

        store.markCompleted(run.runId, processed = 10, written = 8, skipped = 2)
        val completed = store.status(run.runId)
        assertEquals("completed", completed.state)
        assertEquals(10, completed.processedNodes)
        assertEquals(8, completed.writtenChunks)
        assertEquals(2, completed.skippedChunks)
        assertNotNull(completed.finishedAt)
    }

    @Test
    fun `markFailed - добавляет ошибку`() {
        val store = InMemoryChunkRunStore()
        val run = store.create(applicationId = 1L, strategy = "per-node")

        store.markFailed(run.runId, RuntimeException("boom"))
        val failed = store.status(run.runId)
        assertEquals("failed", failed.state)
        assertEquals(listOf("boom"), failed.errors)
        assertNotNull(failed.finishedAt)
    }

    @Test
    fun `status - кидает если runId не найден`() {
        val store = InMemoryChunkRunStore()

        val ex = assertFailsWith<IllegalStateException> {
            store.status("no-such-run")
        }
        assertTrue(ex.message!!.contains("Run not found"))
    }
}

