package com.bftcom.docgenerator.domain.nodedoc

import java.io.Serializable

data class NodeDocId(
    var node: Long? = null,
    var locale: String? = null,
) : Serializable
