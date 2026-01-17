package com.bftcom.docgenerator.postprocessor.utils

import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class MutationMergerTest {

    @Test
    fun `merge - пустой initial и пустые patches возвращает пустую мутацию`() {
        val initial = PartialMutation()
        val patches = emptyList<PartialMutation>()
        
        val result = MutationMerger.merge(initial, patches)
        
        assertThat(result.provided).isEmpty()
    }

    @Test
    fun `merge - initial значения сохраняются когда нет patches`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash1")
            .set(FieldKey.TOKEN_COUNT, 100)
        
        val result = MutationMerger.merge(initial, emptyList())
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("hash1")
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(100)
    }

    @Test
    fun `merge - KEEP_EXISTING не перезаписывает существующее значение`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "existing_hash")
        
        val patch = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "new_hash")
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("existing_hash")
    }

    @Test
    fun `merge - KEEP_EXISTING устанавливает значение если его не было`() {
        val initial = PartialMutation()
        
        val patch = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "new_hash")
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("new_hash")
    }

    @Test
    fun `merge - OVERWRITE всегда перезаписывает значение`() {
        val initial = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model1")
        
        val patch = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model2")
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model2")
    }

    @Test
    fun `merge - OVERWRITE устанавливает значение если его не было`() {
        val initial = PartialMutation()
        
        val patch = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model1")
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model1")
    }

    @Test
    fun `merge - PREFER_LONGER выбирает более длинную строку через рефлексию`() {
        // Тестируем приватный метод pickLonger через рефлексию
        val pickLongerMethod = MutationMerger::class.java.getDeclaredMethod(
            "pickLonger",
            Any::class.java,
            Any::class.java
        )
        pickLongerMethod.isAccessible = true
        
        // Когда b длиннее a
        val result1 = pickLongerMethod.invoke(MutationMerger, "short", "very long string")
        assertThat(result1).isEqualTo("very long string")
        
        // Когда a длиннее b
        val result2 = pickLongerMethod.invoke(MutationMerger, "very long string", "short")
        assertThat(result2).isEqualTo("very long string")
        
        // Когда равны - возвращает a
        val result3 = pickLongerMethod.invoke(MutationMerger, "equal", "equal")
        assertThat(result3).isEqualTo("equal")
        
        // Когда a null - возвращает b
        val result4 = pickLongerMethod.invoke(MutationMerger, null, "value")
        assertThat(result4).isEqualTo("value")
        
        // Когда b null - возвращает a
        val result5 = pickLongerMethod.invoke(MutationMerger, "value", null)
        assertThat(result5).isEqualTo("value")
        
        // Когда оба null - возвращает null
        val result6 = pickLongerMethod.invoke(MutationMerger, null, null)
        assertThat(result6).isNull()
    }
    
    @Test
    fun `merge - GRADE_BETTER выбирает лучший grade через рефлексию`() {
        // Тестируем приватный метод pickBetterGrade через рефлексию
        val pickBetterGradeMethod = MutationMerger::class.java.getDeclaredMethod(
            "pickBetterGrade",
            Any::class.java,
            Any::class.java
        )
        pickBetterGradeMethod.isAccessible = true
        
        // A лучше B
        val result1 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"B","tokens":100}""",
            """{"grade":"A","tokens":200}"""
        )
        assertThat(result1.toString()).contains("\"grade\":\"A\"")
        
        // B лучше C
        val result2 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"C","tokens":50}""",
            """{"grade":"B","tokens":100}"""
        )
        assertThat(result2.toString()).contains("\"grade\":\"B\"")
        
        // A лучше C
        val result3 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"C","tokens":50}""",
            """{"grade":"A","tokens":300}"""
        )
        assertThat(result3.toString()).contains("\"grade\":\"A\"")
        
        // Равные grade - возвращает b
        val result4 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"B","tokens":100}""",
            """{"grade":"B","tokens":150}"""
        )
        assertThat(result4.toString()).contains("\"grade\":\"B\"")
        
        // Когда a null - возвращает b
        val result5 = pickBetterGradeMethod.invoke(
            MutationMerger,
            null,
            """{"grade":"A","tokens":300}"""
        )
        assertThat(result5.toString()).contains("\"grade\":\"A\"")
        
        // Когда b null - возвращает a
        val result6 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"B","tokens":100}""",
            null
        )
        assertThat(result6.toString()).contains("\"grade\":\"B\"")
        
        // Когда оба null - возвращает null
        val result7 = pickBetterGradeMethod.invoke(MutationMerger, null, null)
        assertThat(result7).isNull()
        
        // Когда нет grade в JSON - score = 0, возвращает b
        val result8 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"tokens":100}""",
            """{"tokens":200}"""
        )
        assertThat(result8.toString()).contains("\"tokens\":200")
        
        // Когда grade неизвестный - score = 0, возвращает b
        val result9 = pickBetterGradeMethod.invoke(
            MutationMerger,
            """{"grade":"X","tokens":100}""",
            """{"grade":"Y","tokens":200}"""
        )
        assertThat(result9.toString()).contains("\"tokens\":200")
    }

    @Test
    fun `merge - несколько patches применяются последовательно`() {
        val initial = PartialMutation()
        
        val patch1 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash1")
            .set(FieldKey.TOKEN_COUNT, 100)
        
        val patch2 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash2") // KEEP_EXISTING - не перезапишется
            .set(FieldKey.EMBED_MODEL, "model1")
        
        val result = MutationMerger.merge(initial, listOf(patch1, patch2))
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("hash1") // KEEP_EXISTING
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(100) // KEEP_EXISTING
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model1") // OVERWRITE
    }

    @Test
    fun `merge - последний patch перезаписывает OVERWRITE поля`() {
        val initial = PartialMutation()
        
        val patch1 = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model1")
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        
        val patch2 = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model2")
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-02T00:00:00Z"))
        
        val result = MutationMerger.merge(initial, listOf(patch1, patch2))
        
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model2")
        assertThat(result.provided[FieldKey.EMBED_TS]).isEqualTo(OffsetDateTime.parse("2024-01-02T00:00:00Z"))
    }

    @Test
    fun `merge - TOKEN_COUNT использует KEEP_EXISTING`() {
        val initial = PartialMutation()
            .set(FieldKey.TOKEN_COUNT, 100)
        
        val patch = PartialMutation()
            .set(FieldKey.TOKEN_COUNT, 200)
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(100)
    }

    @Test
    fun `merge - EMB использует OVERWRITE`() {
        val initial = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(1.0f, 2.0f))
        
        val patch = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(3.0f, 4.0f))
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        val resultEmb = result.provided[FieldKey.EMB] as FloatArray
        assertThat(resultEmb).isEqualTo(floatArrayOf(3.0f, 4.0f))
    }

    @Test
    fun `merge - обрабатывает null значения`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash1")
        
        val patch = PartialMutation()
            .set(FieldKey.CONTENT_HASH, null) // KEEP_EXISTING - null не перезапишет
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        // null в patch не должен перезаписать существующее значение для KEEP_EXISTING
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("hash1")
    }

    @Test
    fun `merge - OVERWRITE может установить null`() {
        val initial = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model1")
        
        val patch = PartialMutation()
            .set(FieldKey.EMBED_MODEL, null)
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        // OVERWRITE должен установить null
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isNull()
    }

    @Test
    fun `merge - неизвестное поле использует OVERWRITE по умолчанию`() {
        // Создадим тест с несуществующим полем через рефлексию
        // Но так как FieldKey - enum, нужно использовать существующее поле
        // Протестируем через merge с несколькими патчами
        
        val initial = PartialMutation()
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        
        val patch = PartialMutation()
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-02T00:00:00Z"))
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        // EMBED_TS использует OVERWRITE
        assertThat(result.provided[FieldKey.EMBED_TS]).isEqualTo(OffsetDateTime.parse("2024-01-02T00:00:00Z"))
    }

    @Test
    fun `merge - комплексный сценарий с всеми типами полей`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "initial_hash")
            .set(FieldKey.TOKEN_COUNT, 50)
            .set(FieldKey.EMB, floatArrayOf(1.0f, 2.0f))
            .set(FieldKey.EMBED_MODEL, "initial_model")
        
        val patch1 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "patch1_hash") // KEEP_EXISTING - не перезапишется
            .set(FieldKey.TOKEN_COUNT, 100) // KEEP_EXISTING - не перезапишется
            .set(FieldKey.EMB, floatArrayOf(3.0f, 4.0f)) // OVERWRITE - перезапишется
            .set(FieldKey.EMBED_MODEL, "patch1_model") // OVERWRITE - перезапишется
        
        val patch2 = PartialMutation()
            .set(FieldKey.EMB, floatArrayOf(5.0f, 6.0f)) // OVERWRITE - перезапишется снова
            .set(FieldKey.EMBED_TS, OffsetDateTime.now()) // OVERWRITE - добавится
        
        val result = MutationMerger.merge(initial, listOf(patch1, patch2))
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("initial_hash")
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(50)
        val resultEmb = result.provided[FieldKey.EMB] as FloatArray
        assertThat(resultEmb).isEqualTo(floatArrayOf(5.0f, 6.0f))
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("patch1_model")
        assertThat(result.provided[FieldKey.EMBED_TS]).isNotNull()
    }
    
    @Test
    fun `merge - обрабатывает пустые patches`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash1")
        
        val result = MutationMerger.merge(initial, emptyList())
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("hash1")
    }
    
    @Test
    fun `merge - обрабатывает patch с пустым provided`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "hash1")
        
        val patch = PartialMutation() // Пустой patch
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("hash1")
    }
    
    @Test
    fun `merge - обрабатывает несколько patches с одинаковыми полями`() {
        val initial = PartialMutation()
        
        val patch1 = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model1")
        val patch2 = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model2")
        val patch3 = PartialMutation()
            .set(FieldKey.EMBED_MODEL, "model3")
        
        val result = MutationMerger.merge(initial, listOf(patch1, patch2, patch3))
        
        // Последний patch должен перезаписать (OVERWRITE)
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model3")
    }
    
    @Test
    fun `merge - обрабатывает KEEP_EXISTING с несколькими попытками перезаписи`() {
        val initial = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "original")
        
        val patch1 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "attempt1")
        val patch2 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "attempt2")
        val patch3 = PartialMutation()
            .set(FieldKey.CONTENT_HASH, "attempt3")
        
        val result = MutationMerger.merge(initial, listOf(patch1, patch2, patch3))
        
        // KEEP_EXISTING - должно остаться оригинальное значение
        assertThat(result.provided[FieldKey.CONTENT_HASH]).isEqualTo("original")
    }
    
    @Test
    fun `merge - обрабатывает смешанные типы данных`() {
        val initial = PartialMutation()
            .set(FieldKey.TOKEN_COUNT, 100)
            .set(FieldKey.EMBED_MODEL, "model1")
        
        val patch = PartialMutation()
            .set(FieldKey.TOKEN_COUNT, 200) // KEEP_EXISTING - не перезапишется
            .set(FieldKey.EMBED_MODEL, "model2") // OVERWRITE - перезапишется
            .set(FieldKey.EMBED_TS, OffsetDateTime.parse("2024-01-01T00:00:00Z")) // OVERWRITE - добавится
        
        val result = MutationMerger.merge(initial, listOf(patch))
        
        assertThat(result.provided[FieldKey.TOKEN_COUNT]).isEqualTo(100)
        assertThat(result.provided[FieldKey.EMBED_MODEL]).isEqualTo("model2")
        assertThat(result.provided[FieldKey.EMBED_TS]).isEqualTo(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
    }
}
