package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.domain.node.RawUsage
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class RawUsagePolymorphicDeserializer : JsonDeserializer<RawUsage>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): RawUsage {
        val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)

        // 1) Если уже есть тип
        val typeNode = node.get("@type")?.asText()
        if (typeNode != null) {
            return when (typeNode.lowercase()) {
                "dot" ->
                    RawUsage.Dot(
                        receiver = node.get("receiver")?.asText().orEmpty(),
                        member = node.get("member")?.asText().orEmpty(),
                        isCall = node.get("isCall")?.asBoolean() ?: false,
                    )
                "simple" ->
                    RawUsage.Simple(
                        name = node.get("name")?.asText().orEmpty(),
                        isCall = node.get("isCall")?.asBoolean() ?: true,
                    )
                else ->
                    ctxt.reportInputMismatch(
                        RawUsage::class.java,
                        "Unknown @type for RawUsage: %s",
                        typeNode,
                    )
            }
        }

        // 2) Fallback: определить по форме (для старых данных без @type)
        val hasReceiver = node.has("receiver")
        val hasMember = node.has("member")
        val hasName = node.has("name")

        return when {
            hasReceiver && hasMember ->
                RawUsage.Dot(
                    receiver = node.get("receiver")?.asText().orEmpty(),
                    member = node.get("member")?.asText().orEmpty(),
                    isCall = node.get("isCall")?.asBoolean() ?: false,
                )
            hasName ->
                RawUsage.Simple(
                    name = node.get("name")?.asText().orEmpty(),
                    isCall = node.get("isCall")?.asBoolean() ?: true,
                )
            else ->
                ctxt.reportInputMismatch(
                    RawUsage::class.java,
                    "Cannot infer RawUsage variant from node: %s",
                    node.toString(),
                )
        }
    }
}
