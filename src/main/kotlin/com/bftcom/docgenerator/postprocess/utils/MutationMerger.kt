package com.bftcom.docgenerator.postprocess.utils

import com.bftcom.docgenerator.postprocess.model.FieldKey
import com.bftcom.docgenerator.postprocess.model.MergePolicy
import com.bftcom.docgenerator.postprocess.model.PartialMutation

object MutationMerger {
    private val defaultPolicies: Map<FieldKey, MergePolicy> = mapOf(
        FieldKey.CONTENT_HASH to MergePolicy.KEEP_EXISTING,
        FieldKey.TOKEN_COUNT to MergePolicy.KEEP_EXISTING,
        FieldKey.SPAN_CHARS to MergePolicy.KEEP_EXISTING,
        FieldKey.USES_MD to MergePolicy.KEEP_EXISTING,
        FieldKey.USED_BY_MD to MergePolicy.KEEP_EXISTING,
        FieldKey.EXPLAIN_MD to MergePolicy.PREFER_LONGER,
        FieldKey.EXPLAIN_QUALITY_JSON to MergePolicy.GRADE_BETTER,
        FieldKey.EMB to MergePolicy.OVERWRITE,
        FieldKey.EMBED_MODEL to MergePolicy.OVERWRITE,
        FieldKey.EMBED_TS to MergePolicy.OVERWRITE,
    )

    fun merge(initial: PartialMutation, patches: List<PartialMutation>): PartialMutation {
        val acc = PartialMutation(initial.provided.toMutableMap())
        for (p in patches) {
            for ((k, v) in p.provided) {
                val policy = defaultPolicies[k] ?: MergePolicy.OVERWRITE
                val old = acc.provided[k]
                val newV = when (policy) {
                    MergePolicy.KEEP_EXISTING -> old ?: v
                    MergePolicy.OVERWRITE -> v
                    MergePolicy.PREFER_LONGER -> pickLonger(old, v)
                    MergePolicy.GRADE_BETTER -> pickBetterGrade(old, v)
                }
                acc.provided[k] = newV
            }
        }
        return acc
    }

    private fun pickLonger(a: Any?, b: Any?): Any? {
        val sa = a?.toString() ?: return b
        val sb = b?.toString() ?: return a
        return if (sb.length > sa.length) b else a
    }

    /** Ожидаем JSON вида {"grade":"A|B|C", ...}. Чем ближе к "A", тем лучше. */
    private fun pickBetterGrade(a: Any?, b: Any?): Any? {
        fun score(x: Any?): Int {
            val s = x?.toString() ?: return -1
            val grade = Regex(""""grade"\s*:\s*"(.*?)"""").find(s)?.groupValues?.get(1)
            return when (grade) {
                "A" -> 3; "B" -> 2; "C" -> 1; else -> 0
            }
        }
        return if (score(b) >= score(a)) b else a
    }
}