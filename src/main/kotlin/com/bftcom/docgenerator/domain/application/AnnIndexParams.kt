package com.bftcom.docgenerator.domain.application

data class AnnIndexParams(
    val method: String? = "ivfflat",
    val lists: Int? = 100
)
