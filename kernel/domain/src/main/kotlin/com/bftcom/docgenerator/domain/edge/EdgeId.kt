package com.bftcom.docgenerator.domain.edge

import com.bftcom.docgenerator.domain.enums.EdgeKind
import java.io.Serializable

data class EdgeId(
    var src: Long? = null,
    var dst: Long? = null,
    var kind: EdgeKind? = null,
) : Serializable
