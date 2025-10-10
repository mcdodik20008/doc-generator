package com.bftcom.docgenerator.domain.application

data class OwnerRef(
    val type: String? = null,   // team | user | service
    val id: String? = null,     // идентификатор
    val email: String? = null,  // для user
)
