package com.bftcom.docgenerator.postprocess.model

data class PartialMutation(
    val provided: MutableMap<FieldKey, Any?> = mutableMapOf()
) {
    fun set(k: FieldKey, v: Any?) = apply { provided[k] = v }
    fun has(k: FieldKey) = provided.containsKey(k)
}