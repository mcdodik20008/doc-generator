package com.bftcom.docgenerator.postprocessor.utils

import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MutationMergerTest {
    @Test
    fun `merge - KEEP_EXISTING сохраняет старое значение`() {
        val initial = PartialMutation(mutableMapOf(FieldKey.CONTENT_HASH to "old"))
        val patch = PartialMutation().set(FieldKey.CONTENT_HASH, "new")

        val merged = MutationMerger.merge(initial, listOf(patch))
        assertThat(merged.provided[FieldKey.CONTENT_HASH]).isEqualTo("old")
    }

    @Test
    fun `merge - PREFER_LONGER выбирает более длинный текст`() {
        val initial = PartialMutation().set(FieldKey.EXPLAIN_MD, "short")
        val patch = PartialMutation().set(FieldKey.EXPLAIN_MD, "this is longer")

        val merged = MutationMerger.merge(initial, listOf(patch))
        assertThat(merged.provided[FieldKey.EXPLAIN_MD]).isEqualTo("this is longer")
    }

    @Test
    fun `merge - GRADE_BETTER выбирает более высокий grade`() {
        val initial = PartialMutation().set(FieldKey.EXPLAIN_QUALITY_JSON, """{"grade":"C"}""")
        val patch = PartialMutation().set(FieldKey.EXPLAIN_QUALITY_JSON, """{"grade":"A"}""")

        val merged = MutationMerger.merge(initial, listOf(patch))
        assertThat(merged.provided[FieldKey.EXPLAIN_QUALITY_JSON]).isEqualTo("""{"grade":"A"}""")
    }

    @Test
    fun `merge - OVERWRITE всегда перезаписывает`() {
        val initial = PartialMutation().set(FieldKey.EMBED_MODEL, "old")
        val patch = PartialMutation().set(FieldKey.EMBED_MODEL, "new")

        val merged = MutationMerger.merge(initial, listOf(patch))
        assertThat(merged.provided[FieldKey.EMBED_MODEL]).isEqualTo("new")
    }
}

