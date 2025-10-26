package com.bftcom.docgenerator.ai.props

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

@Validated
@ConfigurationProperties(prefix = "spring.ai.clients")
data class AiClientsProperties(
    val coder: ClientProps,
    val talker: ClientProps
) {
    @Validated
    data class ClientProps(
        @field:NotBlank val model: String,
        val temperature: Double? = null,
        val topP: Double? = null,
        val seed: Int? = null,
        @field:NotBlank val system: String
    )
}
