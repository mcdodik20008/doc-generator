package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.model.RawUsage
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class RawUsagePolymorphicSerializer : JsonSerializer<RawUsage>() {
    override fun serialize(
        value: RawUsage,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeStartObject()
        when (value) {
            is RawUsage.Dot -> {
                gen.writeStringField("@type", "dot")
                gen.writeStringField("receiver", value.receiver)
                gen.writeStringField("member", value.member)
                gen.writeBooleanField("isCall", value.isCall)
            }
            is RawUsage.Simple -> {
                gen.writeStringField("@type", "simple")
                gen.writeStringField("name", value.name)
                gen.writeBooleanField("isCall", value.isCall)
            }
        }
        gen.writeEndObject()
    }
}
