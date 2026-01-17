package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.OffsetDateTime

class HashTokensHandlerTest {

    private val handler = HashTokensHandler()

    @Test
    fun `supports - всегда возвращает true`() {
        val snapshot = createSnapshot(content = "test")
        
        assertThat(handler.supports(snapshot)).isTrue()
    }

    @Test
    fun `supports - возвращает true для любого контента`() {
        assertThat(handler.supports(createSnapshot(content = ""))).isTrue()
        assertThat(handler.supports(createSnapshot(content = "test"))).isTrue()
        assertThat(handler.supports(createSnapshot(content = "very long content"))).isTrue()
    }

    @Test
    fun `produce - вычисляет правильный SHA-256 хэш`() {
        val content = "hello world"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        val expectedHash = MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo(expectedHash)
    }

    @Test
    fun `produce - вычисляет правильное количество токенов`() {
        val content = "hello world test"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(3)
    }

    @Test
    fun `produce - обрабатывает пустой контент`() {
        val snapshot = createSnapshot(content = "")
        
        val result = handler.produce(snapshot)
        
        val expectedHash = MessageDigest
            .getInstance("SHA-256")
            .digest("".toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo(expectedHash)
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(0)
    }

    @Test
    fun `produce - обрабатывает контент с множественными пробелами`() {
        val content = "  hello   world  test  "
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(3)
    }

    @Test
    fun `produce - обрабатывает контент с переносами строк`() {
        val content = "hello\nworld\ntest"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(3)
    }

    @Test
    fun `produce - обрабатывает контент с табуляцией`() {
        val content = "hello\tworld\ttest"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(3)
    }

    @Test
    fun `produce - обрабатывает контент с различными символами`() {
        val content = "hello, world! test 123"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(4)
    }

    @Test
    fun `produce - обрабатывает кириллицу`() {
        val content = "Привет, мир!"
        val snapshot = createSnapshot(content = content)
        
        val result = handler.produce(snapshot)
        
        val expectedHash = MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo(expectedHash)
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(2)
    }

    @Test
    fun `produce - одинаковый контент дает одинаковый хэш`() {
        val content = "test content"
        val snapshot1 = createSnapshot(content = content)
        val snapshot2 = createSnapshot(content = content)
        
        val result1 = handler.produce(snapshot1)
        val result2 = handler.produce(snapshot2)
        
        assertThat(result1.provided[FieldKey.CONTENT_HASH])
            .isEqualTo(result2.provided[FieldKey.CONTENT_HASH])
        assertThat(result1.provided[FieldKey.TOKEN_COUNT])
            .isEqualTo(result2.provided[FieldKey.TOKEN_COUNT])
    }

    @Test
    fun `produce - разный контент дает разный хэш`() {
        val snapshot1 = createSnapshot(content = "content1")
        val snapshot2 = createSnapshot(content = "content2")
        
        val result1 = handler.produce(snapshot1)
        val result2 = handler.produce(snapshot2)
        
        assertThat(result1.provided[FieldKey.CONTENT_HASH])
            .isNotEqualTo(result2.provided[FieldKey.CONTENT_HASH])
    }

    @Test
    fun `produce - устанавливает оба поля`() {
        val snapshot = createSnapshot(content = "test")
        
        val result = handler.produce(snapshot)
        
        assertThat(result.provided).containsKey(FieldKey.CONTENT_HASH)
        assertThat(result.provided).containsKey(FieldKey.TOKEN_COUNT)
        assertThat(result.provided.size).isEqualTo(2)
    }

    @Test
    fun `produce - хэш имеет правильный формат (64 hex символа)`() {
        val snapshot = createSnapshot(content = "test")
        
        val result = handler.produce(snapshot)
        
        val hash = result.provided[FieldKey.CONTENT_HASH] as String
        assertThat(hash).hasSize(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    private fun createSnapshot(
        id: Long = 1L,
        content: String = "test content",
        contentHash: String? = null,
        tokenCount: Int? = null,
        embeddingPresent: Boolean = false,
        embedModel: String? = null,
        embedTs: OffsetDateTime? = null,
    ): ChunkSnapshot {
        return ChunkSnapshot(
            id = id,
            content = content,
            contentHash = contentHash,
            tokenCount = tokenCount,
            embeddingPresent = embeddingPresent,
            embedModel = embedModel,
            embedTs = embedTs,
        )
    }
}
