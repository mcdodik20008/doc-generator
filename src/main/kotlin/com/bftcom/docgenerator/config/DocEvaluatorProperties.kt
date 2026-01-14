package com.bftcom.docgenerator.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Конфигурация для doc-evaluator сервиса. */
@ConfigurationProperties(prefix = "doc-evaluator")
data class DocEvaluatorProperties(
        val baseUrl: String = "http://localhost:8000",
        val timeout: Long = 30,
)
