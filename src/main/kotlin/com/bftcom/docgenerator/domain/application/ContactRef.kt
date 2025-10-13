package com.bftcom.docgenerator.domain.application

data class ContactRef(
    val kind: String? = null, // slack | email | telegram | url
    val value: String? = null, // "#alerts", "dev@...", "https://..."
)
